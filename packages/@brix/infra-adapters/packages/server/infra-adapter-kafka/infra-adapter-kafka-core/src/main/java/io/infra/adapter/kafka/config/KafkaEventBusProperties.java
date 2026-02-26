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
package io.infra.adapter.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 事件总线配置属性
 * 
 * <p>定义 Kafka 事件总线的配置项，对应 application.yml 中的 {@code brix.infra.kafka} 前缀。</p>
 * 
 * <pre>{@code
 * brix:
 *   infra:
 *     kafka:
 *       enabled: true
 *       bootstrap-servers: localhost:9092
 *       topic-prefix: dev-
 *       outbox:
 *         batch-size: 100
 *         max-retry-count: 5
 *         retention-days: 7
 *         process-interval-ms: 1000
 *         retry-interval-ms: 30000
 *         cleanup-cron: "0 0 3 * * ?"
 * }</pre>
 * 
 * @author Brix Platform Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.kafka")
public class KafkaEventBusProperties {

    /**
     * 是否启用 Kafka 事件总线
     * 
     * <p>默认启用。设置为 false 时将不会创建 Kafka 相关 Bean</p>
     */
    private boolean enabled = true;

    /**
     * Kafka Bootstrap Servers 地址
     * 
     * <p>多个地址用逗号分隔，如：localhost:9092,localhost:9093</p>
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * Topic 前缀
     * 
     * <p>用于多环境隔离，如：dev-, staging-, prod-</p>
     */
    private String topicPrefix = "";

    /**
     * 生产者配置
     */
    private ProducerProperties producer = new ProducerProperties();

    /**
     * 消费者配置
     */
    private ConsumerProperties consumer = new ConsumerProperties();

    /**
     * Outbox 模式配置
     * 
     * <p>控制 Outbox 事件发布器的批次大小、重试策略、清理周期等参数。</p>
     */
    private OutboxProperties outbox = new OutboxProperties();

    /**
     * Health check configuration.
     *
     * <p>Controls health indicator behavior for Actuator endpoint.</p>
     * <p>健康检查配置，控制 Actuator 端点的健康指示器行为。</p>
     */
    private HealthProperties health = new HealthProperties();

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public ProducerProperties getProducer() {
        return producer;
    }

    public void setProducer(ProducerProperties producer) {
        this.producer = producer;
    }

    public ConsumerProperties getConsumer() {
        return consumer;
    }

    public void setConsumer(ConsumerProperties consumer) {
        this.consumer = consumer;
    }

    public OutboxProperties getOutbox() {
        return outbox;
    }

    public void setOutbox(OutboxProperties outbox) {
        this.outbox = outbox;
    }

    public HealthProperties getHealth() {
        return health;
    }

    public void setHealth(HealthProperties health) {
        this.health = health;
    }

    // ==================== 嵌套配置类 ====================

    /**
     * 生产者配置
     */
    public static class ProducerProperties {

        /**
         * 确认模式：all, 1, 0
         */
        private String acks = "all";

        /**
         * 重试次数
         */
        private int retries = 3;

        /**
         * 批量大小（字节）
         */
        private int batchSize = 16384;

        /**
         * 等待时间（毫秒）
         */
        private int lingerMs = 5;

        /**
         * 缓冲区大小（字节）
         */
        private int bufferMemory = 33554432;

        // Getters and Setters
        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = acks;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getLingerMs() {
            return lingerMs;
        }

        public void setLingerMs(int lingerMs) {
            this.lingerMs = lingerMs;
        }

        public int getBufferMemory() {
            return bufferMemory;
        }

        public void setBufferMemory(int bufferMemory) {
            this.bufferMemory = bufferMemory;
        }
    }

    /**
     * 消费者配置
     */
    public static class ConsumerProperties {

        /**
         * 消费者组 ID
         */
        private String groupId;

        /**
         * 自动偏移重置策略：earliest, latest, none
         */
        private String autoOffsetReset = "earliest";

        /**
         * 是否自动提交偏移
         */
        private boolean enableAutoCommit = false;

        /**
         * 最大拉取记录数
         */
        private int maxPollRecords = 500;

        // Getters and Setters
        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        public boolean isEnableAutoCommit() {
            return enableAutoCommit;
        }

        public void setEnableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        public int getMaxPollRecords() {
            return maxPollRecords;
        }

        public void setMaxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
        }
    }

    /**
     * Outbox 模式配置
     * 
     * <p>Outbox 模式通过数据库表暂存待发布事件，定时批量发送至 Kafka，
     * 确保事件发布与业务数据的最终一致性。</p>
     * 
     * <p>配置项控制以下行为：</p>
     * <ul>
     *   <li>{@code batchSize} - 每次轮询处理的最大事件数</li>
     *   <li>{@code maxRetryCount} - 发送失败的最大重试次数</li>
     *   <li>{@code retentionDays} - 已完成事件的保留天数</li>
     *   <li>{@code processIntervalMs} - Outbox 轮询间隔（毫秒）</li>
     *   <li>{@code retryIntervalMs} - 失败事件重试间隔（毫秒）</li>
     *   <li>{@code cleanupCron} - 已完成事件清理的 Cron 表达式</li>
     * </ul>
     */
    public static class OutboxProperties {

        /** 每次处理的最大事件批次大小 */
        private int batchSize = 100;

        /** 发送失败后的最大重试次数 */
        private int maxRetryCount = 5;

        /** 已完成事件的保留天数（超过后自动清理） */
        private int retentionDays = 7;

        /** Outbox 事件处理轮询间隔（毫秒） */
        private long processIntervalMs = 1000;

        /** 失败事件重试轮询间隔（毫秒） */
        private long retryIntervalMs = 30000;

        /** 已完成事件清理的 Cron 表达式（默认每日凌晨 3 点） */
        private String cleanupCron = "0 0 3 * * ?";

        // ==================== Getters & Setters ====================

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getMaxRetryCount() { return maxRetryCount; }
        public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }

        public long getProcessIntervalMs() { return processIntervalMs; }
        public void setProcessIntervalMs(long processIntervalMs) { this.processIntervalMs = processIntervalMs; }

        public long getRetryIntervalMs() { return retryIntervalMs; }
        public void setRetryIntervalMs(long retryIntervalMs) { this.retryIntervalMs = retryIntervalMs; }

        public String getCleanupCron() { return cleanupCron; }
        public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }
    }

    /**
     * Health check configuration properties.
     *
     * <p>Controls the behavior of the Kafka health indicator for Spring Boot Actuator.</p>
     * <p>健康检查配置，用于控制 Spring Boot Actuator 的 Kafka 健康指示器行为。</p>
     */
    public static class HealthProperties {

        /**
         * Timeout in seconds for health check operations.
         * 健康检查操作超时时间（秒）
         */
        private int timeoutSeconds = 5;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
