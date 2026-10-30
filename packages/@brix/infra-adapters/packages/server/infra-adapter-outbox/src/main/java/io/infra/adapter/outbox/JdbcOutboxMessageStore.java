/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxBacklogSnapshot;
import io.runtime.orchestrator.outbox.OutboxMessageState;
import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.orchestrator.outbox.ParkedOutboxMessage;

/**
 * JDBC-backed canonical outbox store for the L2B relay.
 *
 * <p>The store operates only on the configured canonical outbox table. It uses
 * conditional updates for claim ownership so multiple relay instances cannot
 * own the same active lease. A parked row keeps the time it was parked in
 * {@code available_at}; nothing claims it until it is replayed.</p>
 *
 * <p>Messages leave in the order the database numbered them ({@code seq}, an identity column of the
 * canonical table), not by the producers' clocks. A message is claimed only while every earlier message
 * of its partition key that is still pending or in flight belongs to the same claim; the claim UPDATE
 * checks this itself, so two relays that read the same candidates cannot split one key between them
 * (root BRX-RS-MESSAGE-027). A claimed row that does not form a canonical message is parked with
 * {@value #ERROR_INVALID_ROW} instead of failing the claim.</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
public final class JdbcOutboxMessageStore implements OutboxMessageStore {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_FLIGHT = "IN_FLIGHT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_PARKED = "PARKED";
    private static final String STATUS_DISCARDED = "DISCARDED";

    /** Error code of a claimed row that does not form a canonical message. */
    public static final String ERROR_INVALID_ROW = "OUTBOX_INVALID_ROW";

    private static final Logger log = LoggerFactory.getLogger(JdbcOutboxMessageStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String tableName;

    /**
     * Creates a JDBC outbox store.
     *
     * @param jdbcTemplate JDBC template
     * @param transactionTemplate transaction template
     * @param tableName canonical outbox table name
     */
    public JdbcOutboxMessageStore(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.tableName = validateTableName(tableName);
    }

    @Override
    public void append(CanonicalOutboxMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        // event_type is the canonical outbox's name of the message type (root MESSAGE-026); the payload
        // column is JSON, so PostgreSQL needs an explicit cast from the bound text.
        jdbcTemplate.update("""
            INSERT INTO %s (
                message_id, event_id, message_kind, message_type, event_type, schema_version,
                reliability, producer_plugin_id, scope, tenant_id, partition_key,
                occurred_at, correlation_id, causation_id, traceparent, tracestate,
                payload, status, available_at, attempt_count, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, %s, ?, ?, ?, ?)
            """.formatted(tableName, jsonPlaceholder()),
            message.messageId(),
            message.eventId(),
            message.messageKind(),
            message.messageType(),
            message.messageType(),
            message.schemaVersion(),
            message.reliability(),
            message.producerPluginId(),
            message.scope(),
            message.tenantId(),
            message.partitionKey(),
            timestamp(message.occurredAt()),
            message.correlationId(),
            message.causationId(),
            message.traceparent(),
            message.tracestate(),
            message.payload(),
            STATUS_PENDING,
            timestamp(message.occurredAt()),
            0,
            timestamp(message.occurredAt()));
    }

    @Override
    public List<CanonicalOutboxMessage> claimDue(
            String relayOwner,
            OffsetDateTime now,
            Duration leaseDuration,
            int batchSize) {
        requireRelayOwner(relayOwner);
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return transactionTemplate.execute(status -> {
            // A message stays behind an earlier one of its partition key that waits for its retry or is in
            // flight (root BRX-RS-MESSAGE-027); due messages of one key come together, in order.
            List<Candidate> candidates = jdbcTemplate.query("""
                SELECT m.message_id, m.partition_key, m.seq
                  FROM %1$s m
                 WHERE m.reliability IN ('CRITICAL', 'STANDARD')
                   AND (
                        (m.status = ? AND m.available_at <= ?)
                     OR (m.status = ? AND m.claim_until < ?)
                   )
                   AND NOT EXISTS (
                        SELECT 1
                          FROM %1$s e
                         WHERE e.partition_key = m.partition_key
                           AND e.seq < m.seq
                           AND e.reliability IN ('CRITICAL', 'STANDARD')
                           AND (
                                (e.status = ? AND e.available_at > ?)
                             OR (e.status = ? AND e.claim_until >= ?)
                           )
                   )
                 ORDER BY m.seq
                 LIMIT ?
                """.formatted(tableName),
                (rs, rowNum) -> new Candidate(rs.getString(1), rs.getString(2), rs.getLong(3)),
                STATUS_PENDING,
                timestamp(now),
                STATUS_IN_FLIGHT,
                timestamp(now),
                STATUS_PENDING,
                timestamp(now),
                STATUS_IN_FLIGHT,
                timestamp(now),
                batchSize);
            List<CanonicalOutboxMessage> claimed = new ArrayList<>();
            OffsetDateTime claimUntil = now.plus(leaseDuration);
            for (Candidate candidate : candidates) {
                // Every earlier message of the key that is still pending or in flight must be ours already:
                // another relay may hold one that this transaction cannot see as claimed yet.
                int updated = jdbcTemplate.update("""
                    UPDATE %1$s
                       SET status = ?,
                           claim_owner = ?,
                           claim_until = ?,
                           attempt_count = attempt_count + 1
                     WHERE message_id = ?
                       AND (
                            (status = ? AND available_at <= ?)
                         OR (status = ? AND claim_until < ?)
                       )
                       AND NOT EXISTS (
                            SELECT 1
                              FROM %1$s e
                             WHERE e.partition_key = ?
                               AND e.seq < ?
                               AND e.reliability IN ('CRITICAL', 'STANDARD')
                               AND e.status IN (?, ?)
                               AND NOT (e.status = ? AND e.claim_owner = ?)
                       )
                    """.formatted(tableName),
                    STATUS_IN_FLIGHT,
                    relayOwner,
                    timestamp(claimUntil),
                    candidate.messageId(),
                    STATUS_PENDING,
                    timestamp(now),
                    STATUS_IN_FLIGHT,
                    timestamp(now),
                    candidate.partitionKey(),
                    candidate.seq(),
                    STATUS_PENDING,
                    STATUS_IN_FLIGHT,
                    STATUS_IN_FLIGHT,
                    relayOwner);
                if (updated == 1) {
                    CanonicalOutboxMessage message = loadClaimed(relayOwner, candidate.messageId(), now);
                    if (message != null) {
                        claimed.add(message);
                    }
                }
            }
            return claimed;
        });
    }

    @Override
    public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
        requireRelayOwner(relayOwner);
        requireText(messageId, "messageId");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        int updated = jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   published_at = ?,
                   last_error_code = NULL
             WHERE message_id = ?
               AND status = ?
               AND claim_owner = ?
            """.formatted(tableName),
            STATUS_PUBLISHED,
            timestamp(publishedAt),
            messageId,
            STATUS_IN_FLIGHT,
            relayOwner);
        requireUpdated(updated, messageId);
    }

    @Override
    public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
        requireRelayOwner(relayOwner);
        requireText(messageId, "messageId");
        requireText(errorCode, "errorCode");
        Objects.requireNonNull(availableAt, "availableAt must not be null");
        int updated = jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   available_at = ?,
                   claim_owner = NULL,
                   claim_until = NULL,
                   last_error_code = ?
             WHERE message_id = ?
               AND status = ?
               AND claim_owner = ?
            """.formatted(tableName),
            STATUS_PENDING,
            timestamp(availableAt),
            errorCode,
            messageId,
            STATUS_IN_FLIGHT,
            relayOwner);
        requireUpdated(updated, messageId);
    }

    @Override
    public void releaseUntried(String relayOwner, String messageId) {
        requireRelayOwner(relayOwner);
        requireText(messageId, "messageId");
        int updated = jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   claim_owner = NULL,
                   claim_until = NULL,
                   attempt_count = CASE WHEN attempt_count > 0 THEN attempt_count - 1 ELSE 0 END
             WHERE message_id = ?
               AND status = ?
               AND claim_owner = ?
            """.formatted(tableName),
            STATUS_PENDING,
            messageId,
            STATUS_IN_FLIGHT,
            relayOwner);
        requireUpdated(updated, messageId);
    }

    @Override
    public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
        requireRelayOwner(relayOwner);
        requireText(messageId, "messageId");
        requireText(errorCode, "errorCode");
        Objects.requireNonNull(parkedAt, "parkedAt must not be null");
        int updated = jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   available_at = ?,
                   claim_owner = NULL,
                   claim_until = NULL,
                   last_error_code = ?
             WHERE message_id = ?
               AND status = ?
               AND claim_owner = ?
            """.formatted(tableName),
            STATUS_PARKED,
            timestamp(parkedAt),
            errorCode,
            messageId,
            STATUS_IN_FLIGHT,
            relayOwner);
        requireUpdated(updated, messageId);
    }

    @Override
    public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        Long pending = countStatus(STATUS_PENDING);
        Long inFlight = countStatus(STATUS_IN_FLIGHT);
        Long parked = countStatus(STATUS_PARKED);
        OffsetDateTime oldest = jdbcTemplate.query("""
            SELECT MIN(created_at)
              FROM %s
             WHERE status = ?
            """.formatted(tableName), rs -> rs.next() ? offsetDateTime(rs, 1) : null, STATUS_PENDING);
        Duration oldestAge = oldest == null ? Duration.ZERO : Duration.between(oldest, now);
        return new OutboxBacklogSnapshot(
            pending != null ? pending : 0L,
            inFlight != null ? inFlight : 0L,
            parked != null ? parked : 0L,
            oldestAge);
    }

    @Override
    public List<ParkedOutboxMessage> listParked(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.query("""
            SELECT event_id, message_id, message_type, schema_version, producer_plugin_id, tenant_id,
                   attempt_count, last_error_code, occurred_at, available_at
              FROM %s
             WHERE status = ?
             ORDER BY available_at, message_id
             LIMIT ?
            """.formatted(tableName), (rs, rowNum) -> new ParkedOutboxMessage(
                rs.getString("event_id"),
                rs.getString("message_id"),
                rs.getString("message_type"),
                rs.getString("schema_version"),
                rs.getString("producer_plugin_id"),
                tenantId(rs),
                rs.getInt("attempt_count"),
                rs.getString("last_error_code"),
                offsetDateTime(rs, "occurred_at"),
                offsetDateTime(rs, "available_at")),
            STATUS_PARKED,
            limit);
    }

    @Override
    public boolean replayParked(String eventId, OffsetDateTime now) {
        requireText(eventId, "eventId");
        Objects.requireNonNull(now, "now must not be null");
        return jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   available_at = ?,
                   attempt_count = 0,
                   last_error_code = NULL
             WHERE event_id = ?
               AND status = ?
            """.formatted(tableName),
            STATUS_PENDING,
            timestamp(now),
            eventId,
            STATUS_PARKED) > 0;
    }

    @Override
    public Optional<OutboxMessageState> find(String eventId) {
        requireText(eventId, "eventId");
        return jdbcTemplate.query("SELECT status, published_at FROM %s WHERE event_id = ? ORDER BY seq DESC"
                .formatted(tableName),
            rs -> rs.next()
                ? Optional.of(new OutboxMessageState(rs.getString(1), offsetDateTime(rs, 2)))
                : Optional.<OutboxMessageState>empty(),
            eventId);
    }

    @Override
    public boolean discardParked(String eventId, OffsetDateTime now) {
        requireText(eventId, "eventId");
        Objects.requireNonNull(now, "now must not be null");
        return jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   available_at = ?
             WHERE event_id = ?
               AND status = ?
            """.formatted(tableName),
            STATUS_DISCARDED,
            timestamp(now),
            eventId,
            STATUS_PARKED) > 0;
    }

    @Override
    public boolean republish(String eventId, OffsetDateTime now, OffsetDateTime publishedSince) {
        requireText(eventId, "eventId");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(publishedSince, "publishedSince must not be null");
        return jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   available_at = ?,
                   attempt_count = 0,
                   claim_owner = NULL,
                   claim_until = NULL,
                   published_at = NULL,
                   last_error_code = ?
             WHERE event_id = ?
               AND status = ?
               AND published_at >= ?
            """.formatted(tableName),
            STATUS_PENDING,
            timestamp(now),
            "REPUBLISHED",
            eventId,
            STATUS_PUBLISHED,
            timestamp(publishedSince)) > 0;
    }

    @Override
    public int purge(OffsetDateTime before, int limit) {
        Objects.requireNonNull(before, "before must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.update("""
            DELETE FROM %1$s
             WHERE message_id IN (
                SELECT message_id
                  FROM %1$s
                 WHERE (status = ? AND published_at < ?)
                    OR (status = ? AND available_at < ?)
                 LIMIT ?)
            """.formatted(tableName),
            STATUS_PUBLISHED,
            timestamp(before),
            STATUS_DISCARDED,
            timestamp(before),
            limit);
    }

    private CanonicalOutboxMessage loadClaimed(String relayOwner, String messageId, OffsetDateTime now) {
        try {
            return jdbcTemplate.queryForObject("""
                SELECT message_id, event_id, message_kind, message_type, schema_version,
                       reliability, producer_plugin_id, scope, tenant_id, partition_key,
                       occurred_at, correlation_id, causation_id, traceparent, tracestate,
                       payload, attempt_count
                  FROM %s
                 WHERE message_id = ?
                   AND status = ?
                   AND claim_owner = ?
                """.formatted(tableName), this::mapMessage, messageId, STATUS_IN_FLIGHT, relayOwner);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            // A row that is not a canonical message would otherwise roll back every claim of this outbox.
            jdbcTemplate.update("""
                UPDATE %s
                   SET status = ?,
                       available_at = ?,
                       claim_owner = NULL,
                       claim_until = NULL,
                       last_error_code = ?
                 WHERE message_id = ?
                   AND claim_owner = ?
                """.formatted(tableName),
                STATUS_PARKED,
                timestamp(now),
                ERROR_INVALID_ROW,
                messageId,
                relayOwner);
            log.warn("event=outbox.parked table={} message_id={} code={}", tableName, messageId, ERROR_INVALID_ROW,
                invalid);
            return null;
        }
    }

    private CanonicalOutboxMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        Long tenantId = tenantId(rs);
        return new CanonicalOutboxMessage(
            rs.getString("message_id"),
            rs.getString("event_id"),
            rs.getString("message_kind"),
            rs.getString("message_type"),
            rs.getString("schema_version"),
            rs.getString("reliability"),
            rs.getString("producer_plugin_id"),
            rs.getString("scope"),
            tenantId,
            rs.getString("partition_key"),
            offsetDateTime(rs, "occurred_at"),
            rs.getString("correlation_id"),
            rs.getString("causation_id"),
            rs.getString("traceparent"),
            rs.getString("tracestate"),
            rs.getString("payload"),
            rs.getInt("attempt_count"));
    }

    private static Long tenantId(ResultSet rs) throws SQLException {
        long tenantId = rs.getLong("tenant_id");
        return rs.wasNull() ? null : tenantId;
    }

    private Long countStatus(String status) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM %s WHERE status = ?".formatted(tableName),
            Long.class,
            status);
    }

    private static String validateTableName(String tableName) {
        requireText(tableName, "tableName");
        if (!tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("tableName must be a simple SQL identifier");
        }
        return tableName;
    }

    private static void requireRelayOwner(String relayOwner) {
        requireText(relayOwner, "relayOwner");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireUpdated(int updated, String messageId) {
        if (updated != 1) {
            throw new IllegalStateException("No active relay claim for message: " + messageId);
        }
    }

    private static Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.from(value.toInstant());
    }

    private static OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
    }

    private static OffsetDateTime offsetDateTime(ResultSet rs, int column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
    }

    private record Candidate(String messageId, String partitionKey, long seq) {
    }

    private volatile String jsonPlaceholder;

    private String jsonPlaceholder() {
        String placeholder = jsonPlaceholder;
        if (placeholder == null) {
            String product = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());
            placeholder = "PostgreSQL".equals(product) ? "CAST(? AS JSONB)" : "?";
            jsonPlaceholder = placeholder;
        }
        return placeholder;
    }

}
