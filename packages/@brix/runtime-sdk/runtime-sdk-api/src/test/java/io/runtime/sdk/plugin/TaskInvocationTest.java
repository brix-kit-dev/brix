/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

/**
 * The task ABI (root BRX-RS-MANIFEST-027, MANIFEST-028; standard-link plan L6.1).
 */
class TaskInvocationTest {

    private static final Instant DUE = Instant.parse("2026-10-31T09:00:00Z");

    @Test
    void anInvocationWithoutItsTaskTriggerOrAPositiveAttemptFailsBeforeTheTaskRuns() {
        assertThrows(IllegalArgumentException.class, () -> invocation(" ", "2026-10-31T09:00:00Z", 1));
        assertThrows(IllegalArgumentException.class, () -> invocation("sample.heartbeat", "", 1));
        assertThrows(IllegalArgumentException.class, () -> invocation("sample.heartbeat", "2026-10-31T09:00:00Z", 0));
        assertThrows(NullPointerException.class, () -> new TaskInvocation("sample.heartbeat", "t", DUE,
            ExecutionIdentity.service("sample"), InvocationScope.platform(), null, new TraceContext("00-1-2-01", null),
            null, CancellationSignal.none(), new IdempotencyContext("sample.heartbeat", "t"), 1));
    }

    @Test
    void aTaskRunsAsItsModuleWithNoAccessOfAnyone() {
        TaskInvocation invocation = invocation("sample.heartbeat", "2026-10-31T09:00:00Z", 2);

        assertEquals(ExecutionIdentity.Kind.SERVICE, invocation.executionIdentity().kind());
        assertEquals(AccessDecision.none(), invocation.access());
        assertEquals(2, invocation.attempt());
    }

    @Test
    void aTaskWrittenAgainstRunStillRunsThroughExecute() {
        List<String> runs = new ArrayList<>();
        @SuppressWarnings("removal")
        ManagedTask legacy = new ManagedTask() {
            @Override
            public void run() {
                runs.add("run");
            }
        };

        assertTrue(legacy.execute(invocation("sample.heartbeat", "t", 1)).toCompletableFuture().isDone());
        assertEquals(List.of("run"), runs);
    }

    @Test
    void aTaskThatImplementsNeitherMethodFailsWhenItRuns() {
        ManagedTask empty = new ManagedTask() {
        };

        assertThrows(UnsupportedOperationException.class, () -> empty.execute(invocation("sample.heartbeat", "t", 1)));
    }

    @Test
    void aTaskImplementsExecute() {
        ManagedTask task = new ManagedTask() {
            @Override
            public CompletionStage<Void> execute(TaskInvocation invocation) {
                return CompletableFuture.completedFuture(null);
            }
        };

        assertTrue(task.execute(invocation("sample.heartbeat", "t", 1)).toCompletableFuture().isDone());
    }

    private static TaskInvocation invocation(String taskId, String triggerId, int attempt) {
        return new TaskInvocation(taskId, triggerId, DUE, ExecutionIdentity.service("sample"), InvocationScope.platform(),
            null, new TraceContext("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null), DUE.plusSeconds(60),
            CancellationSignal.none(), new IdempotencyContext(taskId.isBlank() ? "x" : taskId, triggerId.isBlank() ? "x"
                : triggerId), attempt);
    }
}
