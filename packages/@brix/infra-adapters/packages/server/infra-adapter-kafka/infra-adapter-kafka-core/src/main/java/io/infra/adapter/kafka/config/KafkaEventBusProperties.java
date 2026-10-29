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

import java.time.Duration;

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
 *         publish-timeout-ms: 10000
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
     * The outbox relay's Kafka transport.
     */
    private OutboxProperties outbox = new OutboxProperties();

    /**
     * Health check configuration.
     *
     * <p>Controls health indicator behavior for Actuator endpoint.</p>
     */
    private HealthProperties health = new HealthProperties();

    /**
     * Canonical event bus configuration: the topics created from the manifests and the subscriptions'
     * consumers, used when {@code brix.outbox.relay.transport=kafka}.
     */
    private EventsProperties events = new EventsProperties();

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

    public EventsProperties getEvents() {
        return events;
    }

    public void setEvents(EventsProperties events) {
        this.events = events;
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
     * The outbox relay's Kafka transport.
     */
    public static class OutboxProperties {

        /** How long the canonical outbox transport waits for the broker to take a record. */
        private long publishTimeoutMs = 10000;

        public long getPublishTimeoutMs() { return publishTimeoutMs; }
        public void setPublishTimeoutMs(long publishTimeoutMs) { this.publishTimeoutMs = publishTimeoutMs; }
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

    /**
     * Canonical event bus properties (Kafka design §4.4, §11.2).
     */
    public static class EventsProperties {

        /** Partitions of each event topic; the order of one partition key holds inside a partition. */
        private int partitions = 3;

        /** Replicas of each topic. */
        private short replicationFactor = 1;

        /** How long an event topic keeps its records. */
        private Duration retention = Duration.ofDays(7);

        /** Records a subscription's consumer fetches at most per poll. */
        private int maxPollRecords = 10;

        /** How long a consumer waits for records per poll. */
        private Duration pollTimeout = Duration.ofMillis(500);

        /** How long a broker reachability probe is trusted. */
        private Duration readyCacheTtl = Duration.ofSeconds(5);

        /** How long a call to the broker's admin interface may take. */
        private Duration adminTimeout = Duration.ofSeconds(10);

        /** How long writing a parking record or a release may take. */
        private Duration sendTimeout = Duration.ofSeconds(10);

        public int getPartitions() { return partitions; }
        public void setPartitions(int partitions) { this.partitions = partitions; }

        public short getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(short replicationFactor) { this.replicationFactor = replicationFactor; }

        public Duration getRetention() { return retention; }
        public void setRetention(Duration retention) { this.retention = retention; }

        public int getMaxPollRecords() { return maxPollRecords; }
        public void setMaxPollRecords(int maxPollRecords) { this.maxPollRecords = maxPollRecords; }

        public Duration getPollTimeout() { return pollTimeout; }
        public void setPollTimeout(Duration pollTimeout) { this.pollTimeout = pollTimeout; }

        public Duration getReadyCacheTtl() { return readyCacheTtl; }
        public void setReadyCacheTtl(Duration readyCacheTtl) { this.readyCacheTtl = readyCacheTtl; }

        public Duration getAdminTimeout() { return adminTimeout; }
        public void setAdminTimeout(Duration adminTimeout) { this.adminTimeout = adminTimeout; }

        public Duration getSendTimeout() { return sendTimeout; }
        public void setSendTimeout(Duration sendTimeout) { this.sendTimeout = sendTimeout; }
    }
}
