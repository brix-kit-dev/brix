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
 * Kafka Event Bus Configuration Properties.
 * 
 * <p>Defines configuration items for Kafka event bus, corresponding to the {@code brix.infra.kafka} prefix in application.yml.</p>
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
     * Whether to enable Kafka event bus.
     * 
     * <p>Enabled by default. When set to false, Kafka-related Beans will not be created.</p>
     */
    private boolean enabled = true;

    /**
     * Kafka Bootstrap Servers address.
     * 
     * <p>Multiple addresses separated by commas, e.g.: localhost:9092,localhost:9093</p>
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * Topic prefix.
     * 
     * <p>Used for multi-environment isolation, e.g.: dev-, staging-, prod-</p>
     */
    private String topicPrefix = "";

    /**
     * Producer configuration.
     */
    private ProducerProperties producer = new ProducerProperties();

    /**
     * Consumer configuration.
     */
    private ConsumerProperties consumer = new ConsumerProperties();

    /**
     * Outbox pattern configuration.
     * 
     * <p>Controls Outbox event publisher's batch size, retry strategy, cleanup cycle, etc.</p>
     */
    private OutboxProperties outbox = new OutboxProperties();

    /**
     * Health check configuration.
     *
     * <p>Controls health indicator behavior for Actuator endpoint.</p>
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

    // ==================== Nested Configuration Classes ====================

    /**
     * Producer configuration.
     */
    public static class ProducerProperties {

        /**
         * Acknowledgment mode: all, 1, 0
         */
        private String acks = "all";

        /**
         * Retry count.
         */
        private int retries = 3;

        /**
         * Batch size (bytes).
         */
        private int batchSize = 16384;

        /**
         * Linger time (milliseconds).
         */
        private int lingerMs = 5;

        /**
         * Buffer memory size (bytes).
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
     * Consumer configuration.
     */
    public static class ConsumerProperties {

        /**
         * Consumer group ID.
         */
        private String groupId;

        /**
         * Auto offset reset strategy: earliest, latest, none
         */
        private String autoOffsetReset = "earliest";

        /**
         * Whether to enable auto commit of offsets.
         */
        private boolean enableAutoCommit = false;

        /**
         * Maximum poll records.
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
     * Outbox Pattern Configuration.
     * 
     * <p>The Outbox pattern temporarily stores events to be published in a database table,
     * then sends them to Kafka in scheduled batches, ensuring eventual consistency between
     * event publishing and business data.</p>
     * 
     * <p>Configuration items control the following behaviors:</p>
     * <ul>
     *   <li>{@code batchSize} - Maximum number of events processed per poll</li>
     *   <li>{@code maxRetryCount} - Maximum retry count for failed sends</li>
     *   <li>{@code retentionDays} - Retention days for completed events</li>
     *   <li>{@code processIntervalMs} - Outbox polling interval (milliseconds)</li>
         *   <li>{@code retryIntervalMs} - Failed event retry interval (milliseconds)</li>
         *   <li>{@code cleanupCron} - Cron expression for completed event cleanup</li>
         *   <li>{@code dlqTopicSuffix} - Dead-letter topic suffix for permanently failed events</li>
         * </ul>
         */
    public static class OutboxProperties {

        /** Maximum event batch size per processing. */
        private int batchSize = 100;

        /** Maximum retry count after send failure. */
        private int maxRetryCount = 5;

        /** Retention days for completed events (auto-cleanup after expiration). */
        private int retentionDays = 7;

        /** Outbox event processing polling interval (milliseconds). */
        private long processIntervalMs = 1000;

        /** Failed event retry polling interval (milliseconds). */
        private long retryIntervalMs = 30000;

        /** Cron expression for completed event cleanup (default: 3 AM daily). */
        private String cleanupCron = "0 0 3 * * ?";

        /** Dead-letter topic suffix appended to original topic. */
        private String dlqTopicSuffix = ".DLQ";

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

        public String getDlqTopicSuffix() { return dlqTopicSuffix; }
        public void setDlqTopicSuffix(String dlqTopicSuffix) { this.dlqTopicSuffix = dlqTopicSuffix; }
    }

    /**
     * Health check configuration properties.
     *
     * <p>Controls the behavior of the Kafka health indicator for Spring Boot Actuator.</p>
     */
    public static class HealthProperties {

        /**
         * Timeout in seconds for health check operations.
         */
        private int timeoutSeconds = 5;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
