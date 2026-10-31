/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.time.Duration;
import java.time.Instant;

/**
 * What the scheduler reports (standard-link plan L6.6). Labels are the module and the task, finite sets; never a
 * tenant, user or payload (root BRX-RS-OTEL-006).
 */
public interface TaskMetrics {

    /** Records nothing. */
    TaskMetrics NOOP = new TaskMetrics() {
    };

    /**
     * A task was published.
     *
     * @param moduleId the module
     * @param taskId the task
     * @param interval the shortest interval of its trigger
     * @param at when
     */
    default void published(String moduleId, String taskId, Duration interval, Instant at) {
    }

    /**
     * A run started.
     *
     * @param moduleId the module
     * @param taskId the task
     */
    default void started(String moduleId, String taskId) {
    }

    /**
     * A run ended.
     *
     * @param moduleId the module
     * @param taskId the task
     * @param outcome {@code succeeded}, {@code failed} or {@code cancelled}
     * @param took how long the run took, attempts included
     * @param at when it ended
     */
    default void finished(String moduleId, String taskId, String outcome, Duration took, Instant at) {
    }

    /**
     * A trigger was not run here.
     *
     * @param moduleId the module
     * @param taskId the task
     * @param reason {@code overlap}, {@code claimed} or {@code not_started}
     */
    default void skipped(String moduleId, String taskId, String reason) {
    }
}
