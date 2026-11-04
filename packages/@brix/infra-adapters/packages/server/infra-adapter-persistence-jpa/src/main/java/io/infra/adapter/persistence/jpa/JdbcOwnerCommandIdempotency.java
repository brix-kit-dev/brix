/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.runtime.orchestrator.command.CommandRecord;
import io.runtime.orchestrator.command.OwnerCommandIdempotency;
import io.runtime.orchestrator.outbox.CanonicalMessagingTables;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.sdk.command.CommandStatus;

/**
 * Writes a provider's business idempotency record to its {@code {storageId}_command_idempotency} table, in the
 * transaction that runs the handler (root BRX-RS-MESSAGE-014; Kafka design §14 K22), and the outcome of a command
 * that took no key to its {@code {storageId}_command_outcome} table (standard-link plan point 7). The sender's
 * status reads both by command id.
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class JdbcOwnerCommandIdempotency implements OwnerCommandIdempotency {

    private final OwnerDirectory owners;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcOwnerCommandIdempotency(OwnerDirectory owners, DataSource dataSource, Clock clock) {
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Takes the key with the command's fingerprint (standard-link plan point 8, design D69). On PostgreSQL, the
     * reference DCP of the messaging tables, {@code ON CONFLICT DO NOTHING} waits for a transaction that is inserting
     * the same key: when it commits, nothing is inserted; when it rolls back, this insert goes through. Either way this
     * transaction goes on, so a concurrent command with the same key no longer fails its delivery and spends an
     * attempt. The wait ends with the owner's unit of work, whose statements stop at the delivery's deadline.
     */
    @Override
    public boolean reserve(String ownerId, String handlerId, CanonicalOutboxMessage message, String idempotencyScope,
            String idempotencyKey, String fingerprint) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The command idempotency of " + ownerId
                + " is written only inside the owner's transaction");
        }
        return jdbc.update("INSERT INTO " + keysOf(ownerId) + " (handler_id, idempotency_scope, idempotency_key,"
                + " command_id, command_type, schema_version, tenant_id, status, request_fingerprint, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, 'ACCEPTED', ?, ?) ON CONFLICT DO NOTHING",
            handlerId, idempotencyScope, idempotencyKey, message.messageId(), message.messageType(),
            message.schemaVersion(), message.tenantId(), fingerprint, Timestamp.from(clock.instant())) > 0;
    }

    @Override
    public Optional<Holder> holder(String ownerId, String handlerId, String idempotencyScope, String idempotencyKey) {
        List<Holder> holders = jdbc.query("SELECT command_id, request_fingerprint FROM " + keysOf(ownerId)
                + " WHERE handler_id = ? AND idempotency_scope = ? AND idempotency_key = ?",
            (row, number) -> new Holder(row.getString(1), row.getString(2)),
            handlerId, idempotencyScope, idempotencyKey);
        return holders.stream().findFirst();
    }

    @Override
    public void recordOutcome(String ownerId, String handlerId, CommandRecord outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome.state() == CommandStatus.State.SUCCEEDED) {
            throw new IllegalArgumentException("A command that took its key has its record in the key table");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The command outcomes of " + ownerId
                + " are written only inside the owner's transaction");
        }
        String table = outcomesOf(ownerId);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE handler_id = ? AND command_id = ?", Integer.class, handlerId, outcome.commandId());
        if (existing != null && existing > 0) {
            // A command id has one outcome; the inbox keeps a command from being handled twice.
            return;
        }
        jdbc.update("INSERT INTO " + table + " (handler_id, command_id, idempotency_scope, idempotency_key, outcome,"
                + " error_code, holder_command_id, tenant_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            handlerId, outcome.commandId(), outcome.idempotencyScope(), outcome.idempotencyKey(),
            outcome.state().name(), outcome.errorCode(), outcome.holderCommandId(), outcome.tenantId(),
            Timestamp.from(outcome.at().toInstant()));
    }

    @Override
    public Optional<CommandRecord> find(String ownerId, String handlerId, String commandId) {
        List<CommandRecord> held = jdbc.query("SELECT command_id, idempotency_scope, idempotency_key, tenant_id,"
                + " created_at FROM " + keysOf(ownerId) + " WHERE handler_id = ? AND command_id = ?",
            (row, number) -> new CommandRecord(row.getString(1), CommandStatus.State.SUCCEEDED, row.getString(2),
                row.getString(3), tenantOf(row, 4), null, null, at(row, 5)),
            handlerId, commandId);
        if (!held.isEmpty()) {
            return Optional.of(held.get(0));
        }
        List<CommandRecord> outcomes = jdbc.query("SELECT command_id, outcome, idempotency_scope, idempotency_key,"
                + " tenant_id, error_code, holder_command_id, created_at FROM " + outcomesOf(ownerId)
                + " WHERE handler_id = ? AND command_id = ?",
            (row, number) -> new CommandRecord(row.getString(1), CommandStatus.State.valueOf(row.getString(2)),
                row.getString(3), row.getString(4), tenantOf(row, 5), row.getString(6), row.getString(7),
                at(row, 8)),
            handlerId, commandId);
        return outcomes.stream().findFirst();
    }

    @Override
    public List<String> tables() {
        return owners.ownerIds().stream()
            .filter(owners::providesCommands)
            .map(owners::storageIdOf)
            .flatMap(Optional::stream)
            .flatMap(storageId -> Stream.of(CanonicalMessagingTables.commandIdempotencyOf(storageId),
                CanonicalMessagingTables.commandOutcomeOf(storageId)))
            .distinct()
            .toList();
    }

    @Override
    public int purge(String table, OffsetDateTime createdBefore, int limit) {
        if (!tables().contains(table)) {
            throw new IllegalArgumentException("Not a command idempotency or outcome table: " + table);
        }
        List<String> key = table.endsWith("_command_outcome") ? ExpiredRecords.OUTCOME_KEY
            : ExpiredRecords.IDEMPOTENCY_KEY;
        return ExpiredRecords.delete(jdbc, table, key, "created_at", createdBefore, limit);
    }

    private String keysOf(String ownerId) {
        return owners.storageIdOf(ownerId)
            .map(CanonicalMessagingTables::commandIdempotencyOf)
            .orElseThrow(() -> new IllegalStateException(ownerId + " declares no data.storageId for its commands"));
    }

    private String outcomesOf(String ownerId) {
        return owners.storageIdOf(ownerId)
            .map(CanonicalMessagingTables::commandOutcomeOf)
            .orElseThrow(() -> new IllegalStateException(ownerId + " declares no data.storageId for its commands"));
    }

    private static Long tenantOf(ResultSet row, int column) throws SQLException {
        long tenantId = row.getLong(column);
        return row.wasNull() ? null : tenantId;
    }

    private static OffsetDateTime at(ResultSet row, int column) throws SQLException {
        return row.getTimestamp(column).toInstant().atOffset(ZoneOffset.UTC);
    }
}
