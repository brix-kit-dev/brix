/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;

/**
 * Runtime policy for one L2B outbox relay instance. A failed attempt waits
 * {@code retryBackoffBase * 2^(attempt - 1)}, at most {@code retryBackoffMax}, plus up to
 * {@code retryJitterMax} (root BRX-RS-RESILIENCE-002).
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OutboxRelayPolicy(int batchSize, int maxAttempts, Duration leaseDuration,
        Duration retryBackoffBase, Duration retryJitterMax, Duration retryBackoffMax) {

    /** Upper bound of the retry wait when none is configured. */
    public static final Duration DEFAULT_RETRY_BACKOFF_MAX = Duration.ofMinutes(5);

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
        if (retryBackoffMax == null || retryBackoffMax.compareTo(retryBackoffBase) < 0) {
            throw new IllegalArgumentException("retryBackoffMax must be >= retryBackoffBase");
        }
    }

    /**
     * Creates a policy whose retry wait is bounded by {@link #DEFAULT_RETRY_BACKOFF_MAX}.
     */
    public OutboxRelayPolicy(int batchSize, int maxAttempts, Duration leaseDuration,
            Duration retryBackoffBase, Duration retryJitterMax) {
        this(batchSize, maxAttempts, leaseDuration, retryBackoffBase, retryJitterMax,
            retryBackoffBase == null || retryBackoffBase.compareTo(DEFAULT_RETRY_BACKOFF_MAX) <= 0
                ? DEFAULT_RETRY_BACKOFF_MAX
                : retryBackoffBase);
    }

    /**
     * Returns the wait before the next attempt after {@code attempt} failed, without jitter.
     *
     * @param attempt the attempt that failed, counted from 1
     * @return the backoff
     */
    public Duration backoffAfter(int attempt) {
        return RetryBackoff.delay(retryBackoffBase, retryBackoffMax, attempt);
    }

    /**
     * Returns conservative defaults for small hosts.
     */
    public static OutboxRelayPolicy defaults() {
        return new OutboxRelayPolicy(25, 6, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
