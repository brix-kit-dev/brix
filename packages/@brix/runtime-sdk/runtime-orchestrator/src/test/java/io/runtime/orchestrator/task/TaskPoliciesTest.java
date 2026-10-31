/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;

/**
 * The task policy catalog and triggers (root BRX-RS-MANIFEST-025, MANIFEST-026; standard-link plan L6.2, L6.3).
 */
class TaskPoliciesTest {

    private static final TaskPolicies.Limits LIMITS = new TaskPolicies.Limits(Duration.ofMinutes(1),
        Duration.ofMinutes(5), 3, Duration.ofSeconds(1));

    @Test
    void anEveryTriggerIsDueAtMultiplesOfItsPeriodSinceTheEpoch() {
        TaskTrigger every = TaskTrigger.parse("every:PT15M");

        assertEquals(Instant.parse("2026-10-31T09:15:00Z"), every.next(Instant.parse("2026-10-31T09:07:13Z")));
        assertEquals(Instant.parse("2026-10-31T09:30:00Z"), every.next(Instant.parse("2026-10-31T09:15:00Z")));
        assertEquals(Duration.ofMinutes(15), every.shortestInterval());
    }

    @Test
    void aCronTriggerIsDueWhenItsExpressionMatchesInUtc() {
        TaskTrigger nightly = TaskTrigger.parse("cron:0 30 3 * * *");

        assertEquals(Instant.parse("2026-11-01T03:30:00Z"), nightly.next(Instant.parse("2026-10-31T03:30:00Z")));
        assertEquals(Duration.ofDays(1), nightly.shortestInterval());
        assertEquals(Duration.ofSeconds(10), TaskTrigger.parse("cron:*/10 * * * * *").shortestInterval());
    }

    @Test
    void aMalformedTriggerIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TaskTrigger.parse("hourly"));
        assertThrows(IllegalArgumentException.class, () -> TaskTrigger.parse("every:15m"));
        assertThrows(IllegalArgumentException.class, () -> TaskTrigger.parse("every:PT0.5S"));
        assertThrows(IllegalArgumentException.class, () -> TaskTrigger.parse("cron:0 30 3 * *"));
        assertThrows(IllegalArgumentException.class, () -> TaskTrigger.parse("cron:0 0 0 30 2 *"));
    }

    @Test
    void aTaskNamesOnePlatformPolicyOfEachKind() {
        TaskPolicies.Resolved resolved = TaskPolicies.resolve(task("every:PT1H", TaskPolicies.RETRY_TRANSIENT,
            TaskPolicies.IDEMPOTENCY_TRIGGER), LIMITS);

        assertEquals(3, resolved.attempts());
        assertTrue(resolved.claimed());
        assertEquals(Duration.ofMinutes(5), resolved.timeout());
        assertEquals(PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_REQUIRED, resolved.transaction());
        TaskPolicies.Resolved local = TaskPolicies.resolve(task("every:PT1H", TaskPolicies.RETRY_NONE,
            TaskPolicies.IDEMPOTENCY_LOCAL), LIMITS);
        assertEquals(1, local.attempts());
        assertFalse(local.claimed());
    }

    @Test
    void anUnknownPolicyOrATriggerMoreFrequentThanTheHostAllowsIsRefused() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> TaskPolicies.resolve(
            task("every:PT1H", "task-forever", TaskPolicies.IDEMPOTENCY_TRIGGER), LIMITS));
        assertTrue(unknown.getMessage().contains("retry"), unknown.getMessage());
        IllegalArgumentException frequent = assertThrows(IllegalArgumentException.class, () -> TaskPolicies.resolve(
            task("every:PT10S", TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_TRIGGER), LIMITS));
        assertTrue(frequent.getMessage().contains("brix.tasks.min-interval"), frequent.getMessage());
        assertThrows(IllegalArgumentException.class, () -> TaskPolicies.resolve(new PluginRuntimeDescriptor.ProvidedTask(
            "t", "t.v1", TaskPolicies.SCOPE_PLATFORM, TaskPolicies.IDENTITY_SERVICE, "every:PT1H",
            TaskPolicies.TIMEOUT_STANDARD, TaskPolicies.CONCURRENCY_NO_OVERLAP, TaskPolicies.RETRY_NONE,
            TaskPolicies.IDEMPOTENCY_LOCAL, "serializable"), LIMITS));
    }

    static PluginRuntimeDescriptor.ProvidedTask task(String trigger, String retry, String idempotency) {
        return new PluginRuntimeDescriptor.ProvidedTask("sample-orders.heartbeat", "sample-orders.heartbeat.v1",
            TaskPolicies.SCOPE_PLATFORM, TaskPolicies.IDENTITY_SERVICE, trigger, TaskPolicies.TIMEOUT_STANDARD,
            TaskPolicies.CONCURRENCY_NO_OVERLAP, retry, idempotency, null);
    }
}
