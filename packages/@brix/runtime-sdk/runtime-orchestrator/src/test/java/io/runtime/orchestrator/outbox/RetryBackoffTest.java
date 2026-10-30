/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RetryBackoffTest {

    @Test
    void theWaitDoublesWithEveryAttemptUpToTheBound() {
        Duration base = Duration.ofSeconds(5);
        Duration max = Duration.ofMinutes(1);

        assertEquals(Duration.ofSeconds(5), RetryBackoff.delay(base, max, 1));
        assertEquals(Duration.ofSeconds(10), RetryBackoff.delay(base, max, 2));
        assertEquals(Duration.ofSeconds(40), RetryBackoff.delay(base, max, 4));
        assertEquals(max, RetryBackoff.delay(base, max, 5));
        assertEquals(max, RetryBackoff.delay(base, max, 1_000), "no overflow");
    }

    @Test
    void aZeroBaseNeverWaits() {
        assertEquals(Duration.ZERO, RetryBackoff.delay(Duration.ZERO, Duration.ofMinutes(1), 3));
    }
}
