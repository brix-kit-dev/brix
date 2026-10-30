/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;
import java.util.Objects;

/**
 * Exponential retry backoff with an upper bound (root BRX-RS-RESILIENCE-002), shared by the relay and the
 * event consumers. The caller adds its jitter.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class RetryBackoff {

    private RetryBackoff() {
    }

    /**
     * Returns the wait after a failed attempt: {@code base * 2^(attempt - 1)}, at most {@code max}.
     *
     * @param base wait after the first failed attempt
     * @param max upper bound of the wait
     * @param attempt the attempt that failed, counted from 1
     * @return the wait before the next attempt
     */
    public static Duration delay(Duration base, Duration max, int attempt) {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(max, "max must not be null");
        if (base.isZero() || base.isNegative()) {
            return Duration.ZERO;
        }
        int doublings = Math.min(Math.max(attempt, 1) - 1, 30);
        Duration delay;
        try {
            delay = base.multipliedBy(1L << doublings);
        } catch (ArithmeticException overflow) {
            return max;
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }
}
