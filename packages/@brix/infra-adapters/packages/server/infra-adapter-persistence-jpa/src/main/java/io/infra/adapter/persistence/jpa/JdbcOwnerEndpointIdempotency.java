/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.runtime.orchestrator.endpoint.OwnerEndpointIdempotency;
import io.runtime.orchestrator.outbox.CanonicalMessagingTables;

/**
 * Keeps the answers of endpoints whose results the runtime replays in the owner's
 * {@code {storageId}_endpoint_idempotency} table, in the transaction of the call's business write (root
 * BRX-RS-MANIFEST-018; standard-link plan point 10a). The statements are PostgreSQL's, the reference DCP of the platform
 * tables.
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class JdbcOwnerEndpointIdempotency implements OwnerEndpointIdempotency {

    private final OwnerDirectory owners;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcOwnerEndpointIdempotency(OwnerDirectory owners, DataSource dataSource, Clock clock) {
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Takes the key. Design D77: the transaction-level advisory lock answers at once whether a call with the key is
     * running, and goes when the transaction ends, so the insert behind it never waits. Whoever holds the lock has
     * either inserted the key and not finished, or finished; the row tells the second case from a free key.
     */
    @Override
    public Reservation reserve(String ownerId, Key key, String fingerprint, Long tenantId, String traceId,
            Duration retention) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The endpoint idempotency of " + ownerId
                + " is written only inside the owner's transaction");
        }
        String table = tableOf(ownerId);
        Boolean free = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))", Boolean.class,
            table + "|" + key.entryId() + "|" + key.scope() + "|" + key.key());
        if (!Boolean.TRUE.equals(free)) {
            return Reservation.IN_PROGRESS;
        }
        Instant now = clock.instant();
        int taken = jdbc.update("INSERT INTO " + table + " (entry_id, idempotency_scope, idempotency_key,"
                + " request_fingerprint, tenant_id, trace_id, created_at, expires_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
            key.entryId(), key.scope(), key.key(), fingerprint, tenantId, traceId, Timestamp.from(now),
            Timestamp.from(now.plus(retention)));
        if (taken > 0) {
            return Reservation.TAKEN;
        }
        return Reservation.held(jdbc.queryForObject("SELECT request_fingerprint, result_status,"
                + " CAST(result_body AS TEXT), trace_id FROM " + table
                + " WHERE entry_id = ? AND idempotency_scope = ? AND idempotency_key = ?",
            (row, number) -> new Held(row.getString(1), row.getInt(2), row.getString(3), row.getString(4)),
            key.entryId(), key.scope(), key.key()));
    }

    @Override
    public void complete(String ownerId, Key key, int status, String body) {
        jdbc.update("UPDATE " + tableOf(ownerId) + " SET result_status = ?, result_body = CAST(? AS JSONB)"
                + " WHERE entry_id = ? AND idempotency_scope = ? AND idempotency_key = ?",
            status, body, key.entryId(), key.scope(), key.key());
    }

    @Override
    public List<String> tables() {
        return owners.ownerIds().stream()
            .filter(owners::replaysEndpointResults)
            .map(owners::storageIdOf)
            .flatMap(Optional::stream)
            .map(CanonicalMessagingTables::endpointIdempotencyOf)
            .distinct()
            .toList();
    }

    @Override
    public int purge(String table, OffsetDateTime expiredBefore, int limit) {
        if (!tables().contains(table)) {
            throw new IllegalArgumentException("Not an endpoint idempotency table: " + table);
        }
        return ExpiredRecords.delete(jdbc, table, ExpiredRecords.ENDPOINT_KEY, "expires_at", expiredBefore, limit);
    }

    private String tableOf(String ownerId) {
        return owners.storageIdOf(ownerId)
            .map(CanonicalMessagingTables::endpointIdempotencyOf)
            .orElseThrow(() -> new IllegalStateException(ownerId + " declares no data.storageId for its answers"));
    }
}
