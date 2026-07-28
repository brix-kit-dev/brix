/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;

/**
 * Runtime policy for one L2B outbox relay instance.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OutboxRelayPolicy(int batchSize, int maxAttempts, Duration leaseDuration,
        Duration retryBackoffBase, Duration retryJitterMax) {

    /**
     * Creates and validates relay policy.
     */
    public OutboxRelayPolicy {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (retryBackoffBase == null || retryBackoffBase.isNegative()) {
            throw new IllegalArgumentException("retryBackoffBase must be >= 0");
        }
        if (retryJitterMax == null || retryJitterMax.isNegative()) {
            throw new IllegalArgumentException("retryJitterMax must be >= 0");
        }
    }

    /**
     * Returns conservative defaults for small hosts.
     */
    public static OutboxRelayPolicy defaults() {
        return new OutboxRelayPolicy(25, 6, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
