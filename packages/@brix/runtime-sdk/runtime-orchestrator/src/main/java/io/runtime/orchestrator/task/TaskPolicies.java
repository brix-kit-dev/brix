/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;

/**
 * The platform's policies for tasks (root BRX-RS-MANIFEST-025, MANIFEST-026, DATA-028; standard-link plan L6.3 to
 * L6.5, D57). A task names one of each; a name the runtime does not know stops the plugin at startup.
 *
 * <ul>
 *   <li>scope {@value #SCOPE_PLATFORM}: the task runs in the platform scope, which reaches the plugin's global tables
 *   and no tenant's data;</li>
 *   <li>execution identity {@value #IDENTITY_SERVICE}: the task runs as its module, with no user;</li>
 *   <li>trigger {@code every:<duration>} or {@code cron:<expression>} ({@link TaskTrigger}), no more often than the
 *   host's minimum interval;</li>
 *   <li>timeout {@value #TIMEOUT_STANDARD}: the host's task deadline; at the deadline the run is cancelled and counts as
 *   failed;</li>
 *   <li>concurrency {@value #CONCURRENCY_NO_OVERLAP}: a trigger due while the previous run is still running is
 *   skipped and counted;</li>
 *   <li>retry {@value #RETRY_NONE}, or {@value #RETRY_TRANSIENT}: a failed run is tried again for the same trigger, up
 *   to the host's attempts, after a backoff that doubles;</li>
 *   <li>idempotency {@value #IDEMPOTENCY_TRIGGER}: one host runs each trigger, the one that claims
 *   {@code (taskId, triggerId)} in the module's {@code {storageId}_task_run} table; {@value #IDEMPOTENCY_LOCAL}: every
 *   host runs every trigger, for work local to the host.</li>
 * </ul>
 */
public final class TaskPolicies {

    /** The platform scope. */
    public static final String SCOPE_PLATFORM = "task-platform";

    /** The module's own service identity. */
    public static final String IDENTITY_SERVICE = "plugin-service";

    /** The host's task deadline. */
    public static final String TIMEOUT_STANDARD = "task-standard";

    /** A trigger due while the previous run is running is skipped. */
    public static final String CONCURRENCY_NO_OVERLAP = "task-no-overlap";

    /** A failed run is not tried again. */
    public static final String RETRY_NONE = "task-no-retry";

    /** A failed run is tried again for the same trigger. */
    public static final String RETRY_TRANSIENT = "task-transient-retry";

    /** One host runs each trigger. */
    public static final String IDEMPOTENCY_TRIGGER = "task-trigger";

    /** Every host runs every trigger. */
    public static final String IDEMPOTENCY_LOCAL = "task-instance-local";

    private static final Set<String> TRANSACTIONS = Set.of(PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_REQUIRED,
        PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_READ_ONLY, PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_NONE);

    private TaskPolicies() {
    }

    /**
     * The host's limits on tasks.
     *
     * @param minInterval the shortest interval a trigger may have
     * @param timeout the deadline of {@value #TIMEOUT_STANDARD}
     * @param attempts the attempts of {@value #RETRY_TRANSIENT}
     * @param backoff the wait before the second attempt; it doubles for each later one
     */
    public record Limits(Duration minInterval, Duration timeout, int attempts, Duration backoff) {

        /**
         * Checks the limits.
         */
        public Limits {
            requirePositive(minInterval, "minInterval");
            requirePositive(timeout, "timeout");
            requirePositive(backoff, "backoff");
            if (attempts < 1) {
                throw new IllegalArgumentException("attempts must be at least 1");
            }
        }

        /**
         * Returns the defaults of {@code brix.tasks.*}.
         *
         * @return a minute between runs at most once, a five-minute deadline, three attempts a second apart
         */
        public static Limits defaults() {
            return new Limits(Duration.ofMinutes(1), Duration.ofMinutes(5), 3, Duration.ofSeconds(1));
        }

        private static void requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }

    /**
     * A task's policies, resolved.
     *
     * @param trigger when it is due
     * @param timeout how long a run may take
     * @param attempts how often a trigger's run is tried
     * @param claimed whether one host claims each trigger
     * @param transaction the unit of work it runs in
     */
    public record Resolved(TaskTrigger trigger, Duration timeout, int attempts, boolean claimed, String transaction) {
    }

    /**
     * Resolves a task's policies against the host's limits.
     *
     * @param task the declaration
     * @param limits the host's limits
     * @return the resolved policies
     * @throws IllegalArgumentException naming the first reference the runtime does not know, or a trigger more frequent
     *     than the host allows
     */
    public static Resolved resolve(PluginRuntimeDescriptor.ProvidedTask task, Limits limits) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(limits, "limits must not be null");
        require("scope", task.scopePolicyRef(), Set.of(SCOPE_PLATFORM));
        require("execution identity", task.executionIdentityPolicyRef(), Set.of(IDENTITY_SERVICE));
        require("timeout", task.timeoutPolicyRef(), Set.of(TIMEOUT_STANDARD));
        require("concurrency", task.concurrencyPolicyRef(), Set.of(CONCURRENCY_NO_OVERLAP));
        require("retry", task.retryPolicyRef(), Set.of(RETRY_NONE, RETRY_TRANSIENT));
        require("idempotency", task.idempotencyPolicyRef(), Set.of(IDEMPOTENCY_TRIGGER, IDEMPOTENCY_LOCAL));
        require("transaction", task.transaction(), TRANSACTIONS);
        TaskTrigger trigger = TaskTrigger.parse(task.triggerPolicyRef());
        if (trigger.shortestInterval().compareTo(limits.minInterval()) < 0) {
            throw new IllegalArgumentException("trigger '" + trigger.reference() + "' is due every "
                + trigger.shortestInterval() + ", more often than this host allows (brix.tasks.min-interval="
                + limits.minInterval() + ")");
        }
        return new Resolved(trigger, limits.timeout(), RETRY_TRANSIENT.equals(task.retryPolicyRef()) ? limits.attempts() : 1,
            IDEMPOTENCY_TRIGGER.equals(task.idempotencyPolicyRef()), task.transaction());
    }

    private static void require(String kind, String reference, Set<String> known) {
        if (reference == null || !known.contains(reference)) {
            throw new IllegalArgumentException("unknown task " + kind + " policy '" + reference + "'; known: "
                + new java.util.TreeSet<>(known));
        }
    }
}
