/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.infra.adapter.kafka.EventSerializationException;
import io.infra.adapter.kafka.EventTopicResolver;
import io.infra.adapter.kafka.config.KafkaEventBusProperties;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Outbox 模式事件发布器
 *
 * <p>实现 Outbox 模式，保证事件发布的事务一致性。
 * 核心流程：</p>
 * <ol>
 *   <li>业务代码调用 {@link #saveForLater} 将事件保存到 Outbox 表</li>
 *   <li>定时任务 {@link #processOutbox} 读取并发送待处理事件</li>
 *   <li>发送成功后标记事件为已完成</li>
 * </ol>
 *
 * <h3>架构定位</h3>
 * <p>
 * 本类属于 {@code infra-adapter-outbox} 独立模块（Layer 2.5: Adapter 层）。
 * Outbox 是跨基础设施的模式（需要 DB + MQ 协同），因此从 {@code infra-adapter-kafka}
 * 中独立出来。本模块依赖 {@code infra-adapter-kafka} 以复用
 * {@link EventTopicResolver} 和 {@link KafkaEventBusProperties.OutboxProperties}。
 * </p>
 *
 * <h3>事务保证</h3>
 * <p>Outbox 记录与业务数据在同一个数据库事务中写入，
 * 确保"业务操作成功 → 事件已记录"的一致性。</p>
 *
 * <h3>配置外部化</h3>
 * <p>所有定时任务参数均通过 {@link KafkaEventBusProperties.OutboxProperties} 外部化，
 * 可在 {@code application.yml} 中通过 {@code brix.infra.kafka.outbox.*} 前缀配置。</p>
 *
 * <h3>故障恢复</h3>
 * <ul>
 *   <li>发送失败的事件会自动重试（最大重试次数可配置）</li>
 *   <li>超过重试次数的事件标记为 FAILED，需人工介入</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    /** Outbox JPA 仓储 */
    private final OutboxEventRepository outboxRepository;

    /** Kafka 消息模板 */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 事件 Topic 解析器（从 infra-adapter-kafka 复用） */
    private final EventTopicResolver topicResolver;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** Outbox 配置属性（通过 brix.infra.kafka.outbox.* 外部化配置） */
    private final KafkaEventBusProperties.OutboxProperties outboxConfig;

    /**
     * 构造 Outbox 事件发布器
     *
     * @param outboxRepository Outbox JPA 仓储
     * @param kafkaTemplate    Kafka 消息模板
     * @param topicResolver    事件 Topic 解析器
     * @param objectMapper     JSON 序列化器
     * @param outboxConfig     Outbox 外部化配置属性
     */
    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            KafkaEventBusProperties.OutboxProperties outboxConfig) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
        this.topicResolver = Objects.requireNonNull(topicResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.outboxConfig = Objects.requireNonNull(outboxConfig);
    }

    /**
     * 保存事件到 Outbox（用于事务性发布）
     *
     * <p>此方法应在业务事务中调用，确保事件记录与业务数据一起提交。</p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * @Transactional
     * public void createReservation(ReservationCommand cmd) {
     *     // 保存业务数据
     *     Reservation reservation = repository.save(new Reservation(cmd));
     *
     *     // 保存事件到 Outbox（与业务数据同事务）
     *     outboxPublisher.saveForLater(new ReservationCreatedEvent(reservation.getId()));
     * }
     * }</pre>
     *
     * @param event 要发布的集成事件
     * @throws EventSerializationException 如果事件序列化失败
     */
    @Transactional
    public void saveForLater(IntegrationEvent event) {
        Objects.requireNonNull(event, "事件不能为空");

        // 幂等性检查：如果事件已存在，直接返回
        if (outboxRepository.existsByEventId(event.getEventId())) {
            log.warn("事件已存在，跳过保存: eventId={}", event.getEventId());
            return;
        }

        try {
            // 序列化事件为 JSON
            String payload = objectMapper.writeValueAsString(event);

            // 通过 EventTopicResolver 解析目标 Topic
            String topic = topicResolver.resolveIntegrationTopic(event);

            // 创建 Outbox 记录（PENDING 状态）
            OutboxEvent outboxEvent = OutboxEvent.from(event, payload, topic);

            // 保存到数据库（与业务操作在同一事务中）
            outboxRepository.save(outboxEvent);

            log.debug("事件已保存到 Outbox: eventId={}, type={}",
                    event.getEventId(), event.getEventType());

        } catch (JsonProcessingException e) {
            throw new EventSerializationException("事件序列化失败: " + event.getEventType(), e);
        }
    }

    /**
     * 处理 Outbox 中的待发送事件
     *
     * <p>定时任务，按 {@code brix.infra.kafka.outbox.process-interval-ms} 配置的间隔执行。
     * 默认每隔 1 秒轮询一次。</p>
     *
     * <h4>处理流程</h4>
     * <ol>
     *   <li>查询 PENDING 状态的事件（按创建时间升序，限制批次大小）</li>
     *   <li>批量标记为 PROCESSING（乐观锁防止并发重复处理）</li>
     *   <li>逐个发送到 Kafka（同步确认）</li>
     *   <li>成功则标记 COMPLETED，失败则增加重试计数</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${brix.infra.kafka.outbox.process-interval-ms:1000}")
    @Transactional
    public void processOutbox() {
        int batchSize = outboxConfig.getBatchSize();
        // 查询待处理事件
        List<OutboxEvent> events = outboxRepository.findPendingEvents(batchSize);

        if (events.isEmpty()) {
            return;
        }

        log.debug("开始处理 Outbox 事件，数量: {}", events.size());

        // 批量标记为处理中（防止并发重复处理）
        List<UUID> ids = events.stream()
                .map(OutboxEvent::getId)
                .collect(Collectors.toList());
        outboxRepository.markAsProcessing(ids);

        // 逐个发送到 Kafka
        for (OutboxEvent event : events) {
            try {
                sendToKafka(event);
                event.markCompleted();
                outboxRepository.save(event);

                log.debug("Outbox 事件发送成功: eventId={}", event.getEventId());

            } catch (Exception e) {
                handleSendFailure(event, e);
            }
        }
    }

    /**
     * 处理需要重试的失败事件
     *
     * <p>定时任务，按 {@code brix.infra.kafka.outbox.retry-interval-ms} 配置的间隔执行。
     * 默认每 30 秒轮询一次。将 FAILED 状态的事件（未超过最大重试次数）重置为 PENDING。</p>
     */
    @Scheduled(fixedDelayString = "${brix.infra.kafka.outbox.retry-interval-ms:30000}")
    @Transactional
    public void retryFailedEvents() {
        int maxRetry = outboxConfig.getMaxRetryCount();
        int batchSize = outboxConfig.getBatchSize();
        List<OutboxEvent> events = outboxRepository.findRetryableEvents(maxRetry, batchSize);

        if (events.isEmpty()) {
            return;
        }

        log.info("开始重试失败事件，数量: {}", events.size());

        for (OutboxEvent event : events) {
            event.incrementRetryCount();
            event.resetToPending();
            outboxRepository.save(event);
        }
    }

    /**
     * 清理已完成的旧事件
     *
     * <p>定时任务，按 {@code brix.infra.kafka.outbox.cleanup-cron} 配置的 Cron 表达式执行。
     * 默认每天凌晨 3 点清理超过保留天数的已完成事件，防止 Outbox 表无限膨胀。</p>
     */
    @Scheduled(cron = "${brix.infra.kafka.outbox.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupOldEvents() {
        int retentionDays = outboxConfig.getRetentionDays();
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteCompletedBefore(cutoff);

        if (deleted > 0) {
            log.info("清理已完成的 Outbox 事件: {} 条（保留天数: {}）", deleted, retentionDays);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 发送事件到 Kafka
     *
     * <p>构建包含事件元数据 Header 的 ProducerRecord，使用同步方式发送
     * 以确保 Outbox 场景下能准确判断发送结果。</p>
     *
     * @param event Outbox 事件实体
     */
    private void sendToKafka(OutboxEvent event) {
        // 构建 Kafka 消息 Headers，携带事件元数据
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
        headers.add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

        if (event.getSourceModule() != null) {
            headers.add("sourceModule", event.getSourceModule().getBytes(StandardCharsets.UTF_8));
        }

        // 创建 ProducerRecord（使用 routingKey 作为 Partition Key 保证有序）
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(),
                null,
                event.getRoutingKey(),
                event.getPayload(),
                headers
        );

        // 同步发送（Outbox 场景需要确认发送结果）
        kafkaTemplate.send(record).join();
    }

    /**
     * 处理发送失败
     *
     * <p>根据已配置的最大重试次数决定事件状态：
     * 超过上限标记为 FAILED（需人工介入），否则重置为 PENDING 等待下次重试。</p>
     *
     * @param event 发送失败的 Outbox 事件
     * @param e     发送异常
     */
    private void handleSendFailure(OutboxEvent event, Exception e) {
        event.incrementRetryCount();
        int maxRetry = outboxConfig.getMaxRetryCount();

        if (event.getRetryCount() >= maxRetry) {
            // 超过重试次数，标记为失败
            event.markFailed(e.getMessage());
            log.error("Outbox 事件发送失败（已达最大重试次数 {}）: eventId={}, error={}",
                    maxRetry, event.getEventId(), e.getMessage());
        } else {
            // 重置为待处理，等待下次重试
            event.resetToPending();
            log.warn("Outbox 事件发送失败，将重试: eventId={}, retryCount={}, error={}",
                    event.getEventId(), event.getRetryCount(), e.getMessage());
        }

        outboxRepository.save(event);
    }
}
