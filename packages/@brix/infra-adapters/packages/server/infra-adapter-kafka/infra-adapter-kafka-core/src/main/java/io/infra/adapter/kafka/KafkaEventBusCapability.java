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
package io.infra.adapter.kafka;

import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于 Kafka 的事件总线能力实现
 * 
 * <p>本类实现{@link EventBusCapability} Full Product Host 实现
 * 提供基于 Apache Kafka 的事件发布能力。模块通过此实现发布事件，
 * 无需感知 Kafka 的存在。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>事件路由</b>：基{@link EventTopicResolver} 自动解析 Topic</li>
 *   <li><b>消息。</b>：使用 aggregateId/eventId 作为分区键，保证顺序</li>
 *   <li><b>事件追踪</b>：自动添加追Header（traceId, spanId。</li>
 *   <li><b>序列化。</b>：统一使用 JSON 格式</li>
 * </ul>
 * 
 * <h3>消息格式</h3>
 * <p>所有事件消息包含以Header。</p>
 * <ul>
 *   <li>eventId - 事件唯一标识</li>
 *   <li>eventType - 事件类型（完整类名）</li>
 *   <li>timestamp - 事件时间。</li>
 *   <li>sourceModule - 来源模块 ID</li>
 *   <li>traceId - 链路追踪 ID（如果有。</li>
 * </ul>
 * 
 * <h3>线程安全</h3>
 * <p>本类是线程安全的，可以被多个线程并发使用。</p>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 * @see EventBusCapability
 * @see EventTopicResolver
 */
@Capability(
    type = EventBusCapability.class,
    name = "kafka-event-bus",
    description = "基于 Apache Kafka 的事件总线能力实现",
    level = CapabilityLevel.CORE,
    aliases = {"eventBus", "kafkaEventBus"}
)
public class KafkaEventBusCapability implements EventBusCapability {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventBusCapability.class);

    /**
     * 事件 ID Header 名称
     */
    private static final String HEADER_EVENT_ID = "eventId";

    /**
     * 事件类型 Header 名称
     */
    private static final String HEADER_EVENT_TYPE = "eventType";

    /**
     * 时间Header 名称
     */
    private static final String HEADER_TIMESTAMP = "timestamp";

    /**
     * 来源模块 Header 名称
     */
    private static final String HEADER_SOURCE_MODULE = "sourceModule";

    /**     * 发布超时时间（秒）
     */
    private static final int PUBLISH_TIMEOUT_SECONDS = 10;

    /**     * Kafka 消息模板
     * 
     * <p>Spring 自动注入，用于发送消息到 Kafka</p>
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Topic 解析
     * 
     * <p>根据事件类型解析目标 Topic</p>
     */
    private final EventTopicResolver topicResolver;

    /**
     * JSON 序列化器
     * 
     * <p>统一使用 Jackson 进行 JSON 序列化。</p>
     */
    private final ObjectMapper objectMapper;

    /**
     * 当前模块 ID
     * 
     * <p>用于标识事件来源</p>
     */
    private final String currentModuleId;

    /**
     * 构造函数
     * 
     * @param kafkaTemplate Kafka 消息模板，由 Spring 注入
     * @param topicResolver Topic 解析
     * @param objectMapper  JSON 序列化器
     * @param currentModuleId 当前模块 ID，用于标识事件来
     */
    public KafkaEventBusCapability(
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            String currentModuleId) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate 不能为空");
        this.topicResolver = Objects.requireNonNull(topicResolver, "topicResolver 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.currentModuleId = Objects.requireNonNull(currentModuleId, "currentModuleId 不能为空");
    }

    /**
     * 发布领域事件
     * 
     * <p>领域事件在模块内部传播，发送到模块专属性Topic
     * 消息键使用 aggregateId，保证同一聚合根的事件顺序处理。</p>
     * 
     * <h4>Topic 命名规则</h4>
     * <p>domain.{moduleId}.{aggregateType}</p>
     * <p>例如：domain.booking.reservation</p>
     * 
     * @param event 要发布的领域事件，不能为 null
     * @throws IllegalArgumentException 如果事件null
     * @throws EventPublishException    如果发布失败
     */
    @Override
    public void publish(DomainEvent event) {
        // 参数校验
        Objects.requireNonNull(event, "领域事件不能为空");

        // 解析 Topic
        String topic = topicResolver.resolveDomainTopic(event, currentModuleId);
        
        // 获取消息键（聚合ID，保证同一聚合根事件的顺序列
        String key = event.getAggregateId();
        
        // 序列化事
        String payload = serializeEvent(event);
        
        // 构建消息 Header
        Headers headers = buildHeaders(event);
        
        // 创建 Producer Record
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null,  // partition - Kafka 根据 key 自动选择
                key,
                payload,
                headers
        );
        
        // 同步等待发送结果，确保调用方可感知发布失败
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        
        try {
            SendResult<String, String> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (log.isDebugEnabled()) {
                log.debug("领域事件发布成功: eventId={}, topic={}, partition={}, offset={}",
                        event.getEventId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(event.getEventId(),
                    "领域事件发布被中断: eventId=" + event.getEventId(), e);
        } catch (ExecutionException e) {
            throw new EventPublishException(event.getEventId(),
                    "领域事件发布失败: eventId=" + event.getEventId() + ", topic=" + topic, e.getCause());
        } catch (TimeoutException e) {
            throw new EventPublishException(event.getEventId(),
                    "领域事件发布超时: eventId=" + event.getEventId() + ", topic=" + topic, e);
        }
    }

    /**
     * 发布集成事件
     * 
     * <p>集成事件用于跨模块通信，发送到公共集成 Topic
     * Runtime Shell 根据 Manifest 中的订阅声明将事件路由到相应模块。</p>
     * 
     * <h4>Topic 命名规则</h4>
     * <p>integration.{eventType}</p>
     * <p>例如：integration.reservation-created</p>
     * 
     * <h4>投递保/h4>
     * <ul>
     *   <li>至少一次投递（At-Least-Once。</li>
     *   <li>消费者必须实现幂等处。</li>
     * </ul>
     * 
     * @param event 要发布的集成事件，不能为 null
     * @throws IllegalArgumentException 如果事件null
     * @throws EventPublishException    如果发布失败
     */
    @Override
    public void publishIntegration(IntegrationEvent event) {
        // 参数校验
        Objects.requireNonNull(event, "集成事件不能为空");

        // 解析 Topic
        String topic = topicResolver.resolveIntegrationTopic(event);
        
        // 获取消息键（事件 ID 或路由键
        String key = event.getRoutingKey() != null ? event.getRoutingKey() : event.getEventId();
        
        // 序列化事
        String payload = serializeEvent(event);
        
        // 构建消息 Header
        Headers headers = buildIntegrationHeaders(event);
        
        // 创建 Producer Record
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null,  // partition
                key,
                payload,
                headers
        );
        
        // 同步等待发送结果，确保调用方可感知发布失败
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        
        try {
            SendResult<String, String> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("集成事件发布成功: eventId={}, type={}, topic={}, partition={}, offset={}",
                    event.getEventId(),
                    event.getEventType(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(event.getEventId(),
                    "集成事件发布被中断: eventId=" + event.getEventId(), e);
        } catch (ExecutionException e) {
            throw new EventPublishException(event.getEventId(),
                    "集成事件发布失败: eventId=" + event.getEventId() + ", topic=" + topic, e.getCause());
        } catch (TimeoutException e) {
            throw new EventPublishException(event.getEventId(),
                    "集成事件发布超时: eventId=" + event.getEventId() + ", topic=" + topic, e);
        }
    }

    /**
     * 序列化事件为 JSON 字符
     * 
     * @param event 要序列化的事
     * @return JSON 字符
     * @throws EventSerializationException 如果序列化失
     */
    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(
                    "事件序列化失 " + event.getClass().getName(), e);
        }
    }

    /**
     * 构建领域事件的消Header
     * 
     * @param event 领域事件
     * @return Kafka Headers
     */
    private Headers buildHeaders(DomainEvent event) {
        RecordHeaders headers = new RecordHeaders();
        
        // 添加事件元数据
        addHeader(headers, HEADER_EVENT_ID, event.getEventId());
        addHeader(headers, HEADER_EVENT_TYPE, event.getEventType());
        addHeader(headers, HEADER_TIMESTAMP, String.valueOf(event.getTimestamp().toEpochMilli()));
        addHeader(headers, HEADER_SOURCE_MODULE, currentModuleId);
        
        // 添加聚合根信
        addHeader(headers, "aggregateId", event.getAggregateId());
        addHeader(headers, "aggregateType", event.getAggregateType());
        
        return headers;
    }

    /**
     * 构建集成事件的消Header
     * 
     * @param event 集成事件
     * @return Kafka Headers
     */
    private Headers buildIntegrationHeaders(IntegrationEvent event) {
        RecordHeaders headers = new RecordHeaders();
        
        // 添加事件元数据
        addHeader(headers, HEADER_EVENT_ID, event.getEventId());
        addHeader(headers, HEADER_EVENT_TYPE, event.getEventType());
        addHeader(headers, HEADER_TIMESTAMP, String.valueOf(event.getTimestamp().toEpochMilli()));
        addHeader(headers, HEADER_SOURCE_MODULE, event.getSourceModule());
        
        // 添加集成事件特有信息
        if (event.getCorrelationId() != null) {
            addHeader(headers, "correlationId", event.getCorrelationId());
        }
        if (event.getRoutingKey() != null) {
            addHeader(headers, "routingKey", event.getRoutingKey());
        }
        
        return headers;
    }

    /**
     * 添加 Header（如果值不为空
     * 
     * @param headers Header 集合
     * @param key     Header 
     * @param value   Header 
     */
    private void addHeader(RecordHeaders headers, String key, String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
