/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;
import java.util.Objects;

/**
 * The Host's settings for typed queries (root BRX-RS-MESSAGE-005, MESSAGE-006; query design §5.3).
 *
 * <p>{@code maxDeadline} is the platform's hard cap: no query may wait longer, whatever its policies say. The Host
 * Profile must set it explicitly; a host whose plugins provide or call queries does not start without it.</p>
 *
 * @param maxDeadline the hard cap on a query's deadline
 * @param interactiveTimeout the timeout of policy {@value QueryPolicies#TIMEOUT_INTERACTIVE}
 * @param backgroundTimeout the timeout of policy {@value QueryPolicies#TIMEOUT_BACKGROUND}
 * @param standardConcurrency the concurrent calls a query of policy {@value QueryPolicies#CONCURRENCY_STANDARD} takes
 * @param maxDepth how deeply queries may call queries
 * @param breaker the circuit breaker of each caller and query
 * @param retryBackoff the base backoff of policy {@value QueryPolicies#RETRY_TRANSIENT}
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record QuerySettings(
        Duration maxDeadline,
        Duration interactiveTimeout,
        Duration backgroundTimeout,
        int standardConcurrency,
        int maxDepth,
        Breaker breaker,
        Duration retryBackoff) {

    /**
     * Validates the settings.
     */
    public QuerySettings {
        requirePositive(maxDeadline, "maxDeadline");
        requirePositive(interactiveTimeout, "interactiveTimeout");
        requirePositive(backgroundTimeout, "backgroundTimeout");
        requireWithin(interactiveTimeout, maxDeadline, "interactiveTimeout");
        requireWithin(backgroundTimeout, maxDeadline, "backgroundTimeout");
        if (standardConcurrency < 1) {
            throw new IllegalArgumentException("standardConcurrency must be at least 1");
        }
        if (maxDepth < 1 || maxDepth > 16) {
            throw new IllegalArgumentException("maxDepth must be between 1 and 16");
        }
        Objects.requireNonNull(breaker, "breaker must not be null");
        Objects.requireNonNull(retryBackoff, "retryBackoff must not be null");
        if (retryBackoff.isNegative()) {
            throw new IllegalArgumentException("retryBackoff must not be negative");
        }
    }

    /**
     * Returns the defaults of query design §5.3 under a hard cap.
     *
     * @param maxDeadline the hard cap the Host Profile sets
     * @return interactive 2 s and background 10 s (each no longer than the cap), 32 concurrent calls, depth 4
     */
    public static QuerySettings defaults(Duration maxDeadline) {
        requirePositive(maxDeadline, "maxDeadline");
        return new QuerySettings(
            maxDeadline,
            atMost(Duration.ofSeconds(2), maxDeadline),
            atMost(Duration.ofSeconds(10), maxDeadline),
            32,
            4,
            Breaker.defaults(),
            Duration.ofMillis(50));
    }

    /**
     * The circuit breaker of each caller and query: count-based over the last {@code window} calls.
     *
     * @param window how many recent calls count
     * @param minimumCalls how many calls must be seen before the breaker may open
     * @param failureRatePercent the failure rate that opens it
     * @param openFor how long it stays open before letting trial calls through
     * @param halfOpenCalls how many trial calls it lets through
     */
    public record Breaker(int window, int minimumCalls, float failureRatePercent, Duration openFor, int halfOpenCalls) {

        /**
         * Validates the breaker.
         */
        public Breaker {
            if (window < 1 || minimumCalls < 1 || minimumCalls > window) {
                throw new IllegalArgumentException("breaker window and minimumCalls must satisfy 1 <= minimumCalls <= window");
            }
            if (failureRatePercent <= 0 || failureRatePercent > 100) {
                throw new IllegalArgumentException("breaker failureRatePercent must be in (0, 100]");
            }
            requirePositive(openFor, "breaker openFor");
            if (halfOpenCalls < 1) {
                throw new IllegalArgumentException("breaker halfOpenCalls must be at least 1");
            }
        }

        /**
         * Returns the defaults: 50 % of the last 20 calls, at least 10, open 10 s, 3 trial calls.
         *
         * @return the breaker
         */
        public static Breaker defaults() {
            return new Breaker(20, 10, 50f, Duration.ofSeconds(10), 3);
        }
    }

    private static Duration atMost(Duration value, Duration cap) {
        return value.compareTo(cap) <= 0 ? value : cap;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireWithin(Duration value, Duration cap, String name) {
        if (value.compareTo(cap) > 0) {
            throw new IllegalArgumentException(name + " " + value + " exceeds the hard cap " + cap);
        }
    }
}
