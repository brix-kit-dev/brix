/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The versioned platform schema of an Owner's canonical messaging tables (root BRX-RS-MESSAGE-023,
 * MESSAGE-026): what each table must have for the relay, the deliveries and the cleanup to work.
 *
 * <p>The platform creates these tables for each Owner from its versioned migrations under {@value #MIGRATIONS}, one
 * location per {@link Kind}, with {@code {{storageId}}} replaced by the Owner's {@code data.storageId}; an Owner
 * writes no messaging DDL of its own. The persistence and outbox adapters read the actual tables at startup and
 * refuse an Owner whose tables do not match, instead of failing on the first message. The check covers columns,
 * primary keys and indexes; value constraints are the migrations'.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class CanonicalMessagingTables {

    /** Version of the platform schema these rules describe: 2 adds the command key's request fingerprint. */
    public static final int VERSION = 2;

    /**
     * Classpath directory of the PostgreSQL migrations, one subdirectory per {@link Kind}, with
     * {@code {{storageId}}} placeholders.
     */
    public static final String MIGRATIONS = "META-INF/brix/messaging/postgresql";

    /**
     * How long published and discarded outbox messages stay by default (Kafka design D34); a published message can be
     * republished until then.
     */
    public static final Duration OUTBOX_RETENTION = Duration.ofDays(14);

    /**
     * How long inbox and command idempotency records stay by default (Kafka design D34): longer than a message can
     * still arrive, which is the outbox retention plus the broker's.
     */
    public static final Duration INBOX_RETENTION = Duration.ofDays(30);

    private static final List<String> OUTBOX_COLUMNS = List.of(
        "message_id", "seq", "event_id", "message_kind", "message_type", "event_type", "schema_version",
        "tenant_id", "partition_key", "payload", "reliability", "producer_plugin_id", "scope", "correlation_id",
        "causation_id", "traceparent", "tracestate", "status", "occurred_at", "available_at", "attempt_count",
        "claim_owner", "claim_until", "published_at", "last_error_code", "created_at");

    private static final List<String> INBOX_COLUMNS = List.of(
        "handler_id", "message_id", "message_kind", "message_type", "schema_version", "tenant_id", "status",
        "processed_at", "created_at");

    private static final List<String> IDEMPOTENCY_COLUMNS = List.of(
        "handler_id", "idempotency_scope", "idempotency_key", "command_id", "command_type", "schema_version",
        "tenant_id", "status", "request_fingerprint", "created_at");

    private static final List<String> OUTCOME_COLUMNS = List.of(
        "handler_id", "command_id", "idempotency_scope", "idempotency_key", "outcome", "error_code",
        "holder_command_id", "tenant_id", "created_at");

    private static final List<String> TASK_RUN_COLUMNS = List.of(
        "task_id", "trigger_id", "scheduled_at", "claimed_by", "claimed_at", "status", "attempts", "error_code",
        "finished_at");

    /** The canonical tables an Owner can have. */
    public enum Kind {
        /** {@code {storageId}_outbox}: the messages the Owner sends. */
        OUTBOX("outbox", OUTBOX_COLUMNS, List.of("message_id"),
            List.of(List.of("partition_key", "seq"), List.of("status", "available_at"), List.of("event_id"),
                List.of("status", "published_at"), List.of("seq"))),
        /** {@code {storageId}_inbox}: the deliveries the Owner's handlers have applied. */
        INBOX("inbox", INBOX_COLUMNS, List.of("handler_id", "message_id"), List.of(List.of("processed_at"))),
        /** {@code {storageId}_command_idempotency}: the business idempotency keys of the Owner's commands. */
        COMMAND_IDEMPOTENCY("command-idempotency", IDEMPOTENCY_COLUMNS,
            List.of("handler_id", "idempotency_scope", "idempotency_key"),
            List.of(List.of("handler_id", "command_id"), List.of("created_at"))),
        /**
         * {@code {storageId}_command_outcome}: what became of the Owner's commands that did not take their key
         * (standard-link plan point 7).
         */
        COMMAND_OUTCOME("command-outcome", OUTCOME_COLUMNS, List.of("handler_id", "command_id"),
            List.of(List.of("created_at"))),
        /** {@code {storageId}_task_run}: the claimed runs of the Owner's tasks (standard-link plan L6.4). */
        TASK_RUN("task-run", TASK_RUN_COLUMNS, List.of("task_id", "trigger_id"), List.of(List.of("finished_at")));

        private final String part;
        private final List<String> columns;
        private final List<String> primaryKey;
        private final List<List<String>> indexes;

        Kind(String part, List<String> columns, List<String> primaryKey, List<List<String>> indexes) {
            this.part = part;
            this.columns = columns;
            this.primaryKey = primaryKey;
            this.indexes = indexes;
        }

        /**
         * Returns the name of this table's migrations, as their directory and history tables use it.
         *
         * @return {@code outbox}, {@code inbox}, {@code command-idempotency}, {@code command-outcome} or
         *     {@code task-run}
         */
        public String part() {
            return part;
        }

        /**
         * Returns the classpath directory of this table's PostgreSQL migrations.
         *
         * @return the directory under {@link #MIGRATIONS}
         */
        public String migrations() {
            return MIGRATIONS + "/" + part;
        }

        /**
         * Returns this table's name for an Owner.
         *
         * @param storageId the Owner's {@code data.storageId}
         * @return the table name
         */
        public String tableOf(String storageId) {
            return storageId + "_" + part.replace('-', '_');
        }

        /**
         * Returns the columns the table must have.
         *
         * @return column names
         */
        public List<String> columns() {
            return columns;
        }

        /**
         * Returns the primary key, in order.
         *
         * @return key columns
         */
        public List<String> primaryKey() {
            return primaryKey;
        }

        /**
         * Returns the column lists some index (or key) must start with.
         *
         * @return leading index columns
         */
        public List<List<String>> indexes() {
            return indexes;
        }
    }

    /**
     * What the database reports about one table: its columns, primary key and indexes, names in lower case.
     *
     * @param table table name
     * @param columns column names
     * @param primaryKey primary key columns in key order; empty when there is none
     * @param indexes columns of every index and unique key, each in index order
     */
    public record TableShape(String table, Set<String> columns, List<String> primaryKey, List<List<String>> indexes) {

        /**
         * Creates a table shape.
         */
        public TableShape {
            Objects.requireNonNull(table, "table must not be null");
            columns = columns.stream().map(column -> column.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            primaryKey = primaryKey.stream().map(column -> column.toLowerCase(Locale.ROOT)).toList();
            indexes = indexes.stream()
                .map(index -> index.stream().map(column -> column.toLowerCase(Locale.ROOT)).toList())
                .toList();
        }

        /**
         * Returns whether the table was found at all.
         *
         * @return whether the table has columns
         */
        public boolean exists() {
            return !columns.isEmpty();
        }
    }

    private CanonicalMessagingTables() {
    }

    /**
     * Returns the canonical outbox of a storage id.
     *
     * @param storageId the manifest's {@code data.storageId}
     * @return {@code {storageId}_outbox}
     */
    public static String outboxOf(String storageId) {
        return requireStorageId(storageId) + "_outbox";
    }

    /**
     * Returns the canonical inbox of a storage id.
     *
     * @param storageId the manifest's {@code data.storageId}
     * @return {@code {storageId}_inbox}
     */
    public static String inboxOf(String storageId) {
        return requireStorageId(storageId) + "_inbox";
    }

    /**
     * Returns the canonical command idempotency table of a storage id.
     *
     * @param storageId the manifest's {@code data.storageId}
     * @return {@code {storageId}_command_idempotency}
     */
    public static String commandIdempotencyOf(String storageId) {
        return requireStorageId(storageId) + "_command_idempotency";
    }

    /**
     * Returns the canonical command outcome table of a storage id (standard-link plan point 7).
     *
     * @param storageId the manifest's {@code data.storageId}
     * @return {@code {storageId}_command_outcome}
     */
    public static String commandOutcomeOf(String storageId) {
        return requireStorageId(storageId) + "_command_outcome";
    }

    /**
     * Returns an Owner's task-run table.
     *
     * @param storageId the Owner's {@code data.storageId}
     * @return {@code {storageId}_task_run}
     */
    public static String taskRunOf(String storageId) {
        return requireStorageId(storageId) + "_task_run";
    }

    /**
     * Compares a table with the platform schema.
     *
     * @param kind which canonical table it is
     * @param actual what the database reports
     * @return the differences, empty when the table conforms
     */
    public static List<String> problems(Kind kind, TableShape actual) {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(actual, "actual must not be null");
        List<String> problems = new ArrayList<>();
        if (!actual.exists()) {
            problems.add(actual.table() + " does not exist");
            return problems;
        }
        for (String column : kind.columns()) {
            if (!actual.columns().contains(column)) {
                problems.add(actual.table() + " has no column " + column);
            }
        }
        if (!actual.primaryKey().equals(kind.primaryKey())) {
            problems.add(actual.table() + " must have the primary key " + kind.primaryKey() + ", not "
                + actual.primaryKey());
        }
        for (List<String> leading : kind.indexes()) {
            boolean indexed = actual.indexes().stream()
                .anyMatch(index -> index.size() >= leading.size() && index.subList(0, leading.size()).equals(leading));
            if (!indexed) {
                problems.add(actual.table() + " has no index on " + leading);
            }
        }
        return problems;
    }

    private static String requireStorageId(String storageId) {
        if (storageId == null || !storageId.matches("[a-z][a-z0-9_]{0,30}")) {
            throw new IllegalArgumentException("storageId must match [a-z][a-z0-9_]{0,30}: " + storageId);
        }
        return storageId;
    }
}
