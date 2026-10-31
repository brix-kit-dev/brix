/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.time.Instant;
import java.util.Objects;

import io.runtime.sdk.annotation.Since;

/**
 * One run of a declared task, as the runtime's scheduler starts it (root BRX-RS-MANIFEST-027, MANIFEST-028;
 * standard-link plan L6.1). The runtime builds it; the task reads it and replaces nothing in it.
 *
 * @param taskId the manifest's {@code taskId}
 * @param triggerId what this run is for: the same instant on every host, so one run per trigger can be told apart
 *     from a repeated one
 * @param scheduledAt when the trigger was due
 * @param executionIdentity who the task runs as
 * @param scope the scope the task runs in
 * @param access what the access policy admitted; nothing for a task that runs as its module
 * @param trace the run's trace
 * @param deadline when the run must have finished; the runtime cancels it then
 * @param cancellation set when the deadline passes or the host stops
 * @param idempotency the boundary of a repeated run: the task and its trigger
 * @param attempt the attempt for this trigger, from 1
 * @since 3.3.0
 */
@Since("3.3.0")
public record TaskInvocation(
        String taskId,
        String triggerId,
        Instant scheduledAt,
        ExecutionIdentity executionIdentity,
        InvocationScope scope,
        AccessDecision access,
        TraceContext trace,
        Instant deadline,
        CancellationSignal cancellation,
        IdempotencyContext idempotency,
        int attempt) {

    /**
     * Checks the invocation: anything missing, or an attempt below 1, fails before the task runs (root
     * BRX-RS-MANIFEST-028).
     */
    public TaskInvocation {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (triggerId == null || triggerId.isBlank()) {
            throw new IllegalArgumentException("triggerId must not be blank");
        }
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Objects.requireNonNull(executionIdentity, "executionIdentity must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        access = access != null ? access : AccessDecision.none();
        Objects.requireNonNull(trace, "trace must not be null");
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancellation, "cancellation must not be null");
        Objects.requireNonNull(idempotency, "idempotency must not be null");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
    }
}
