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

import java.time.Instant;
import java.util.UUID;

import io.runtime.sdk.event.IntegrationEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Outbox 事件实体
 *
 * <p>用于实现 Outbox 模式，保证事件发布的事务一致性。
 * 事件先写入 Outbox 表，再由异步任务发送到 Kafka。</p>
 *
 * <h3>架构定位</h3>
 * <p>
 * 本类属于 {@code infra-adapter-outbox} 独立模块（Layer 2.5: Adapter 层）。
 * Outbox 是跨基础设施的模式（需要 DB + MQ 协同），因此从 {@code infra-adapter-kafka}
 * 中独立出来，避免 Kafka 适配器引入 JPA 依赖。
 * </p>
 *
 * <h3>Outbox 模式说明</h3>
 * <p>Outbox 模式解决分布式事务问题：</p>
 * <ol>
 *   <li>业务操作和事件写入 Outbox 在同一个数据库事务中完成</li>
 *   <li>后台定时任务读取 Outbox 中未发送的事件</li>
 *   <li>发送到 Kafka 成功后标记事件为已处理</li>
 * </ol>
 *
 * <h3>表结构</h3>
 * <pre>{@code
 * CREATE TABLE event_outbox (
 *     id UUID PRIMARY KEY,
 *     event_id VARCHAR(64) NOT NULL,
 *     event_type VARCHAR(255) NOT NULL,
 *     payload TEXT NOT NULL,
 *     topic VARCHAR(255) NOT NULL,
 *     routing_key VARCHAR(255),
 *     status VARCHAR(32) NOT NULL,
 *     created_at TIMESTAMP NOT NULL,
 *     processed_at TIMESTAMP,
 *     retry_count INT DEFAULT 0,
 *     error_message TEXT,
 *     source_module VARCHAR(128)
 * );
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
@Entity
@Table(name = "event_outbox", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_created", columnList = "created_at"),
        @Index(name = "idx_outbox_event_id", columnList = "event_id", unique = true)
})
public class OutboxEvent {

    /**
     * 事件状态枚举
     *
     * <p>描述 Outbox 事件在整个生命周期中的状态流转：
     * {@code PENDING → PROCESSING → COMPLETED} 或
     * {@code PENDING → PROCESSING → (重试) PENDING → ... → FAILED}</p>
     */
    public enum Status {
        /** 待处理 - 等待发送到 Kafka */
        PENDING,

        /** 处理中 - 正在发送到 Kafka */
        PROCESSING,

        /** 已完成 - 发送到 Kafka 成功 */
        COMPLETED,

        /** 失败 - 发送失败且超过最大重试次数 */
        FAILED
    }

    /** 主键 ID（数据库自动生成的 UUID） */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 事件唯一标识
     *
     * <p>对应 {@link IntegrationEvent#getEventId()}，用于幂等性检查。</p>
     */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /**
     * 事件类型
     *
     * <p>对应 {@link IntegrationEvent#getEventType()}（通常为完整类名）</p>
     */
    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    /** 事件载荷（JSON 格式的序列化数据） */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** 目标 Kafka Topic */
    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    /** 路由键（用于 Kafka Partition Key，保证同一路由键的事件有序） */
    @Column(name = "routing_key", length = 255)
    private String routingKey;

    /** 事件当前状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.PENDING;

    /** 创建时间（事件写入 Outbox 的时间） */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 处理完成时间（发送成功或最终失败的时间） */
    @Column(name = "processed_at")
    private Instant processedAt;

    /** 重试次数（累计发送失败的次数） */
    @Column(name = "retry_count")
    private int retryCount = 0;

    /** 错误消息（最后一次发送失败的异常信息） */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 来源模块 ID（标识哪个业务模块产生的事件） */
    @Column(name = "source_module", length = 128)
    private String sourceModule;

    // ==================== 构造函数 ====================

    /**
     * JPA 默认构造函数
     *
     * <p>仅供 JPA 框架反射调用，业务代码请使用 {@link #from(IntegrationEvent, String, String)} 工厂方法。</p>
     */
    protected OutboxEvent() {
    }

    /**
     * 从集成事件创建 Outbox 记录
     *
     * <p>工厂方法，提取 IntegrationEvent 的关键信息创建 Outbox 实体。
     * 创建后状态为 {@link Status#PENDING}，等待定时任务处理。</p>
     *
     * @param event   集成事件（提供 eventId、eventType、routingKey、sourceModule）
     * @param payload 序列化后的 JSON 载荷
     * @param topic   目标 Kafka Topic（由 EventTopicResolver 解析）
     * @return Outbox 事件实体（PENDING 状态）
     */
    public static OutboxEvent from(IntegrationEvent event, String payload, String topic) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.eventId = event.getEventId();
        outbox.eventType = event.getEventType();
        outbox.payload = payload;
        outbox.topic = topic;
        outbox.routingKey = event.getRoutingKey();
        outbox.sourceModule = event.getSourceModule();
        outbox.status = Status.PENDING;
        outbox.createdAt = Instant.now();
        return outbox;
    }

    // ==================== 业务方法（状态流转） ====================

    /**
     * 标记为处理中
     *
     * <p>在定时任务从数据库取出事件准备发送时调用。</p>
     */
    public void markProcessing() {
        this.status = Status.PROCESSING;
    }

    /**
     * 标记为已完成
     *
     * <p>在事件成功发送到 Kafka 后调用，同时记录处理完成时间。</p>
     */
    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.processedAt = Instant.now();
    }

    /**
     * 标记为失败
     *
     * <p>在事件超过最大重试次数仍无法发送时调用，需人工介入处理。</p>
     *
     * @param errorMessage 失败原因描述
     */
    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = Instant.now();
    }

    /**
     * 增加重试次数
     *
     * <p>每次发送失败时调用，用于判断是否达到最大重试上限。</p>
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 重置为待处理状态（用于重试）
     *
     * <p>发送失败但未超过最大重试次数时，重置状态等待下次定时任务处理。</p>
     */
    public void resetToPending() {
        this.status = Status.PENDING;
    }

    // ==================== Getters ====================

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getTopic() {
        return topic;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", topic='" + topic + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                '}';
    }
}
