/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the L2B outbox relay managed resource.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@ConfigurationProperties(prefix = "brix.outbox.relay")
public class OutboxRelayProperties {

    /**
     * Stable identity for this relay instance.
     */
    private String owner;

    /**
     * How claimed messages leave the outbox: {@code in-process} hands events to the subscribers' persistent
     * inboxes in this process (P4 design D11); {@code kafka} publishes them to the broker.
     */
    private String transport = "in-process";

    /**
     * Maximum claimed messages per polling pass.
     */
    private int batchSize = 25;

    /**
     * Maximum publish attempts before parking.
     */
    private int maxAttempts = 6;

    /**
     * Lease duration for claimed records.
     */
    private Duration leaseDuration = Duration.ofSeconds(30);

    /**
     * Base retry backoff.
     */
    private Duration retryBackoffBase = Duration.ofSeconds(5);

    /**
     * Maximum random retry jitter.
     */
    private Duration retryJitterMax = Duration.ofSeconds(5);

    /**
     * Upper bound of the exponential retry backoff.
     */
    private Duration retryBackoffMax = OutboxRelayPolicy.DEFAULT_RETRY_BACKOFF_MAX;

    /**
     * How long a message waits for a receiving side that cannot take it before it is parked. Keep it below the retention
     * of the event topics.
     */
    private Duration maxWait = OutboxRelayPolicy.DEFAULT_MAX_WAIT;

    /**
     * Fixed delay between polling passes.
     */
    private Duration pollDelay = Duration.ofSeconds(1);

    /**
     * Retention of the messaging tables (Kafka design D34).
     */
    private final Retention retention = new Retention();

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getRetryBackoffBase() {
        return retryBackoffBase;
    }

    public void setRetryBackoffBase(Duration retryBackoffBase) {
        this.retryBackoffBase = retryBackoffBase;
    }

    public Duration getRetryJitterMax() {
        return retryJitterMax;
    }

    public void setRetryJitterMax(Duration retryJitterMax) {
        this.retryJitterMax = retryJitterMax;
    }

    public Duration getRetryBackoffMax() {
        return retryBackoffMax;
    }

    public void setRetryBackoffMax(Duration retryBackoffMax) {
        this.retryBackoffMax = retryBackoffMax;
    }

    public Duration getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(Duration maxWait) {
        this.maxWait = maxWait;
    }

    public Duration getPollDelay() {
        return pollDelay;
    }

    public void setPollDelay(Duration pollDelay) {
        this.pollDelay = pollDelay;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public Retention getRetention() {
        return retention;
    }

    /**
     * Retention of the messaging tables (root BRX-RS-MESSAGE-026; Kafka design D34, §14 K16). Parked messages stay
     * until an operator replays or discards them.
     */
    public static class Retention {

        /**
         * Whether this Host deletes expired messaging records.
         */
        private boolean enabled = true;

        /**
         * How long published and discarded outbox messages stay; a published message can be republished until then.
         */
        private Duration outbox = CanonicalMessagingTables.OUTBOX_RETENTION;

        /**
         * How long inbox and command idempotency records stay: at least the outbox retention plus the broker's.
         */
        private Duration inbox = CanonicalMessagingTables.INBOX_RETENTION;

        /**
         * Delay between cleaning passes.
         */
        private Duration interval = Duration.ofHours(1);

        /**
         * Rows deleted per statement.
         */
        private int batchSize = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getOutbox() {
            return outbox;
        }

        public void setOutbox(Duration outbox) {
            this.outbox = outbox;
        }

        public Duration getInbox() {
            return inbox;
        }

        public void setInbox(Duration inbox) {
            this.inbox = inbox;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
