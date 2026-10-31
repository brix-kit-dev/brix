/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.runtime.orchestrator.outbox.CanonicalMessagingTables;
import io.runtime.orchestrator.task.TaskRunClaims;

/**
 * Claims task triggers in each owner's {@code {storageId}_task_run} table (root BRX-RS-MANIFEST-025; standard-link
 * plan L6.4, D57). A claim is an insert that commits on its own: the host whose insert of {@code (task_id,
 * trigger_id)} commits first runs the trigger, and the others find the key taken.
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class JdbcTaskRunClaims implements TaskRunClaims {

    private final Function<String, Optional<String>> storageIdOf;
    private final Supplier<List<String>> storageIds;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /**
     * Creates the claims.
     *
     * @param storageIdOf the {@code data.storageId} of a module
     * @param storageIds the storage ids of the modules that provide tasks
     * @param dataSource the owners' data source
     * @param clock claim clock
     */
    JdbcTaskRunClaims(Function<String, Optional<String>> storageIdOf, Supplier<List<String>> storageIds,
            DataSource dataSource, Clock clock) {
        this.storageIdOf = Objects.requireNonNull(storageIdOf, "storageIdOf must not be null");
        this.storageIds = Objects.requireNonNull(storageIds, "storageIds must not be null");
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates the claims of the owners the manifests on the class path declare.
     *
     * @param owners the owners
     * @param dataSource their data source
     * @param clock claim clock
     * @return the claims
     */
    static JdbcTaskRunClaims of(OwnerDirectory owners, DataSource dataSource, Clock clock) {
        return new JdbcTaskRunClaims(owners::storageIdOf, () -> owners.ownerIds().stream()
            .filter(owners::providesTasks)
            .map(owners::storageIdOf)
            .flatMap(Optional::stream)
            .toList(), dataSource, clock);
    }

    @Override
    public boolean claim(String moduleId, String taskId, String triggerId, Instant scheduledAt, String instanceId) {
        try {
            jdbc.update("INSERT INTO " + table(moduleId) + " (task_id, trigger_id, scheduled_at, claimed_by, claimed_at,"
                    + " status, attempts) VALUES (?, ?, ?, ?, ?, 'RUNNING', 0)",
                taskId, triggerId, Timestamp.from(scheduledAt), instanceId, Timestamp.from(clock.instant()));
            return true;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public void finish(String moduleId, String taskId, String triggerId, Outcome outcome, int attempts,
            String errorCode) {
        jdbc.update("UPDATE " + table(moduleId) + " SET status = ?, attempts = ?, error_code = ?, finished_at = ?"
                + " WHERE task_id = ? AND trigger_id = ?",
            outcome.name(), attempts, errorCode, Timestamp.from(clock.instant()), taskId, triggerId);
    }

    @Override
    public List<String> tables() {
        return storageIds.get().stream()
            .map(CanonicalMessagingTables::taskRunOf)
            .distinct()
            .toList();
    }

    @Override
    public int purge(String table, OffsetDateTime finishedBefore, int limit) {
        if (!tables().contains(table)) {
            throw new IllegalArgumentException("Not a task-run table: " + table);
        }
        // A run that never finished, because its host stopped, goes by when it was claimed.
        return ExpiredRecords.delete(jdbc, table, ExpiredRecords.TASK_RUN_KEY, "COALESCE(finished_at, claimed_at)",
            finishedBefore, limit);
    }

    private String table(String moduleId) {
        return storageIdOf.apply(moduleId)
            .map(CanonicalMessagingTables::taskRunOf)
            .orElseThrow(() -> new IllegalStateException(moduleId + " declares no data.storageId for its task runs"));
    }
}
