/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.runtime.orchestrator.command.OwnerCommandIdempotency;
import io.runtime.orchestrator.outbox.CanonicalMessagingTables;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Writes a provider's business idempotency record to its {@code {storageId}_command_idempotency} table, in the
 * transaction that runs the handler (root BRX-RS-MESSAGE-014; Kafka design §14 K22).
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

    @Override
    public boolean reserve(String ownerId, String handlerId, CanonicalOutboxMessage message, String idempotencyScope,
            String idempotencyKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The command idempotency of " + ownerId
                + " is written only inside the owner's transaction");
        }
        String table = owners.storageIdOf(ownerId)
            .map(CanonicalMessagingTables::commandIdempotencyOf)
            .orElseThrow(() -> new IllegalStateException(ownerId + " declares no data.storageId for its commands"));
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE handler_id = ? AND idempotency_scope = ? AND idempotency_key = ?",
            Integer.class, handlerId, idempotencyScope, idempotencyKey);
        if (existing != null && existing > 0) {
            return false;
        }
        jdbc.update("INSERT INTO " + table + " (handler_id, idempotency_scope, idempotency_key, command_id, command_type,"
                + " schema_version, tenant_id, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACCEPTED', ?)",
            handlerId, idempotencyScope, idempotencyKey, message.messageId(), message.messageType(),
            message.schemaVersion(), message.tenantId(), Timestamp.from(clock.instant()));
        return true;
    }
}
