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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxBacklogSnapshot;
import io.runtime.orchestrator.outbox.OutboxMessageStore;

/**
 * JDBC-backed canonical outbox store for the L2B relay.
 *
 * <p>The store operates only on the configured canonical outbox table. It uses
 * conditional updates for claim ownership so multiple relay instances cannot
 * own the same active lease.</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
public final class JdbcOutboxMessageStore implements OutboxMessageStore {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_FLIGHT = "IN_FLIGHT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_PARKED = "PARKED";

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
            List<String> candidateIds = jdbcTemplate.queryForList("""
                SELECT message_id
                  FROM %s
                 WHERE reliability IN ('CRITICAL', 'STANDARD')
                   AND (
                        (status = ? AND available_at <= ?)
                     OR (status = ? AND claim_until < ?)
                   )
                 ORDER BY occurred_at, message_id
                 LIMIT ?
                """.formatted(tableName), String.class,
                STATUS_PENDING,
                timestamp(now),
                STATUS_IN_FLIGHT,
                timestamp(now),
                batchSize);
            List<CanonicalOutboxMessage> claimed = new ArrayList<>();
            OffsetDateTime claimUntil = now.plus(leaseDuration);
            for (String messageId : candidateIds) {
                int updated = jdbcTemplate.update("""
                    UPDATE %s
                       SET status = ?,
                           claim_owner = ?,
                           claim_until = ?,
                           attempt_count = attempt_count + 1
                     WHERE message_id = ?
                       AND (
                            (status = ? AND available_at <= ?)
                         OR (status = ? AND claim_until < ?)
                       )
                    """.formatted(tableName),
                    STATUS_IN_FLIGHT,
                    relayOwner,
                    timestamp(claimUntil),
                    messageId,
                    STATUS_PENDING,
                    timestamp(now),
                    STATUS_IN_FLIGHT,
                    timestamp(now));
                if (updated == 1) {
                    claimed.add(loadClaimed(relayOwner, messageId));
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
    public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
        requireRelayOwner(relayOwner);
        requireText(messageId, "messageId");
        requireText(errorCode, "errorCode");
        Objects.requireNonNull(parkedAt, "parkedAt must not be null");
        int updated = jdbcTemplate.update("""
            UPDATE %s
               SET status = ?,
                   claim_owner = NULL,
                   claim_until = NULL,
                   last_error_code = ?
             WHERE message_id = ?
               AND status = ?
               AND claim_owner = ?
            """.formatted(tableName),
            STATUS_PARKED,
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

    private CanonicalOutboxMessage loadClaimed(String relayOwner, String messageId) {
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
    }

    private CanonicalOutboxMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        Long tenantId = rs.getLong("tenant_id");
        if (rs.wasNull()) {
            tenantId = null;
        }
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
}
