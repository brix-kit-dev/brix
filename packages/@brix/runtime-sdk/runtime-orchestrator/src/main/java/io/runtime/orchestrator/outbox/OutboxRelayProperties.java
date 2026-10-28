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
     * Fixed delay between polling passes.
     */
    private Duration pollDelay = Duration.ofSeconds(1);

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
}
