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
 * {@code retryJitterMax} (root BRX-RS-RESILIENCE-002). A message the receiving side cannot take now spends no attempt:
 * it is tried again after {@link #waitAfter}, plus the same jitter, and is parked once it has waited {@code maxWait}
 * (standard-link plan D91).
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OutboxRelayPolicy(int batchSize, int maxAttempts, Duration leaseDuration,
        Duration retryBackoffBase, Duration retryJitterMax, Duration retryBackoffMax, Duration maxWait) {

    /** Upper bound of the retry wait when none is configured. */
    public static final Duration DEFAULT_RETRY_BACKOFF_MAX = Duration.ofMinutes(5);

    /**
     * How long a message waits when nothing is configured. Ordinary outages pass within it, so they park nothing and
     * keep the order of a key, the operators' alerts fire long before it ends, and it stays well within the retention
     * of a topic, which would take a record that waited longer.
     */
    public static final Duration DEFAULT_MAX_WAIT = Duration.ofHours(24);

    /** The shortest pause before a message that waits is tried again. */
    private static final Duration SHORTEST_WAIT = Duration.ofSeconds(1);

    /** The longest pause: a message leaves within it once the receiving side is back. */
    private static final Duration LONGEST_WAIT = Duration.ofMinutes(1);

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
        if (maxWait == null || maxWait.isZero() || maxWait.isNegative()) {
            throw new IllegalArgumentException("maxWait must be positive");
        }
    }

    /**
     * Creates a policy with the {@linkplain #DEFAULT_MAX_WAIT default limit} on a wait.
     */
    public OutboxRelayPolicy(int batchSize, int maxAttempts, Duration leaseDuration,
            Duration retryBackoffBase, Duration retryJitterMax, Duration retryBackoffMax) {
        this(batchSize, maxAttempts, leaseDuration, retryBackoffBase, retryJitterMax, retryBackoffMax, DEFAULT_MAX_WAIT);
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
     * Returns the pause before a message the receiving side could not take is tried again, without jitter. It doubles
     * from {@code retryBackoffBase}, starts at a second at least, and grows no further than a minute or
     * {@code retryBackoffMax}, whichever is shorter.
     *
     * @param times how often the message has waited, this time included, counted from 1
     * @return the pause
     */
    public Duration waitAfter(int times) {
        Duration base = retryBackoffBase.compareTo(SHORTEST_WAIT) < 0 ? SHORTEST_WAIT : retryBackoffBase;
        Duration longest = retryBackoffMax.compareTo(LONGEST_WAIT) < 0 ? retryBackoffMax : LONGEST_WAIT;
        return RetryBackoff.delay(base, longest.compareTo(base) < 0 ? base : longest, times);
    }

    /**
     * Returns conservative defaults for small hosts.
     */
    public static OutboxRelayPolicy defaults() {
        return new OutboxRelayPolicy(25, 6, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
