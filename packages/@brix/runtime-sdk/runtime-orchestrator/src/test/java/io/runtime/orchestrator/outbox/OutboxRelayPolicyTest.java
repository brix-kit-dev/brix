/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** A wait has growing pauses and a limit, as attempts have (root BRX-RS-RESILIENCE-002, standard-link plan D91). */
class OutboxRelayPolicyTest {

    @Test
    void thePausesOfAWaitDoubleUpToAMinute() {
        OutboxRelayPolicy policy = policy(Duration.ofSeconds(5), OutboxRelayPolicy.DEFAULT_RETRY_BACKOFF_MAX);

        assertEquals(Duration.ofSeconds(5), policy.waitAfter(1));
        assertEquals(Duration.ofSeconds(10), policy.waitAfter(2));
        assertEquals(Duration.ofSeconds(40), policy.waitAfter(4));
        assertEquals(Duration.ofMinutes(1), policy.waitAfter(5));
        assertEquals(Duration.ofMinutes(1), policy.waitAfter(1_440), "a day of waiting does not overflow");
    }

    @Test
    void aWaitPausesForASecondAtLeast() {
        OutboxRelayPolicy policy = policy(Duration.ofMillis(100), OutboxRelayPolicy.DEFAULT_RETRY_BACKOFF_MAX);

        assertEquals(Duration.ofSeconds(1), policy.waitAfter(1));
        assertEquals(Duration.ofSeconds(2), policy.waitAfter(2));
    }

    @Test
    void aShorterRetryBoundKeepsTheWaitShorterToo() {
        assertEquals(Duration.ofSeconds(30), policy(Duration.ofSeconds(5), Duration.ofSeconds(30)).waitAfter(10));
        assertEquals(Duration.ofSeconds(1), policy(Duration.ofMillis(100), Duration.ofMillis(500)).waitAfter(10),
            "but it stays at the shortest pause");
    }

    @Test
    void aLongerRetryBaseIsTheWaitsPause() {
        assertEquals(Duration.ofMinutes(2), policy(Duration.ofMinutes(2), Duration.ofMinutes(5)).waitAfter(3));
    }

    @Test
    void aWaitHasALimitByDefaultAndCannotGoWithoutOne() {
        assertEquals(Duration.ofHours(24), OutboxRelayPolicy.defaults().maxWait());
        for (Duration none : new Duration[] { null, Duration.ZERO, Duration.ofSeconds(-1) }) {
            assertThrows(IllegalArgumentException.class, () -> new OutboxRelayPolicy(25, 6, Duration.ofSeconds(30),
                Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofMinutes(5), none));
        }
    }

    private static OutboxRelayPolicy policy(Duration base, Duration max) {
        return new OutboxRelayPolicy(25, 6, Duration.ofSeconds(30), base, Duration.ZERO, max);
    }
}
