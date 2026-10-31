/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The task runs a module's hosts share, in its {@code {storageId}_task_run} table (root BRX-RS-MANIFEST-025;
 * standard-link plan L6.4, D57). The first host to claim {@code (taskId, triggerId)} runs the trigger; the claim
 * commits on its own, before the run, so no lock is held while the task works.
 */
public interface TaskRunClaims {

    /** How a run ended. */
    enum Outcome {
        /** The task completed. */
        SUCCEEDED,
        /** Every attempt failed or ran past its deadline. */
        FAILED,
        /** The host stopped before the run ended. */
        CANCELLED
    }

    /**
     * Claims a trigger for this host.
     *
     * @param moduleId the module the task belongs to
     * @param taskId the task
     * @param triggerId the trigger
     * @param scheduledAt when it was due
     * @param instanceId this host
     * @return {@code true} when this host claimed it, {@code false} when another host had
     */
    boolean claim(String moduleId, String taskId, String triggerId, Instant scheduledAt, String instanceId);

    /**
     * Records how a claimed run ended.
     *
     * @param moduleId the module
     * @param taskId the task
     * @param triggerId the trigger
     * @param outcome how it ended
     * @param attempts how many attempts it took
     * @param errorCode why the last attempt failed, or {@code null}
     */
    void finish(String moduleId, String taskId, String triggerId, Outcome outcome, int attempts, String errorCode);

    /**
     * Returns the task-run tables of this host's modules.
     *
     * @return table names
     */
    List<String> tables();

    /**
     * Deletes finished runs.
     *
     * @param table one of {@link #tables()}
     * @param finishedBefore runs that finished before this go
     * @param limit at most this many rows
     * @return rows deleted
     */
    int purge(String table, OffsetDateTime finishedBefore, int limit);

    /**
     * Returns claims for a host without owner persistence: a task that claims its triggers does not start there.
     *
     * @return claims that refuse to claim
     */
    static TaskRunClaims none() {
        return new TaskRunClaims() {
            @Override
            public boolean claim(String moduleId, String taskId, String triggerId, Instant scheduledAt,
                    String instanceId) {
                throw new IllegalStateException("This host has no owner persistence to claim " + moduleId
                    + " task " + taskId + " in");
            }

            @Override
            public void finish(String moduleId, String taskId, String triggerId, Outcome outcome, int attempts,
                    String errorCode) {
            }

            @Override
            public List<String> tables() {
                return List.of();
            }

            @Override
            public int purge(String table, OffsetDateTime finishedBefore, int limit) {
                return 0;
            }
        };
    }
}
