/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxBacklogSnapshot;

class JdbcOutboxMessageStoreTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-28T01:00:00Z");

    private JdbcTemplate jdbcTemplate;
    private JdbcOutboxMessageStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:outbox_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "sa",
            "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        org.springframework.jdbc.datasource.DataSourceTransactionManager transactionManager =
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        store = new JdbcOutboxMessageStore(jdbcTemplate, new TransactionTemplate(transactionManager),
            "platform_tenant_outbox");
        jdbcTemplate.execute("""
            CREATE TABLE platform_tenant_outbox (
                message_id VARCHAR(64) PRIMARY KEY,
                event_id VARCHAR(64) NOT NULL,
                message_kind VARCHAR(20) NOT NULL,
                message_type VARCHAR(128) NOT NULL,
                schema_version VARCHAR(16) NOT NULL,
                reliability VARCHAR(20) NOT NULL,
                producer_plugin_id VARCHAR(128) NOT NULL,
                scope VARCHAR(20) NOT NULL,
                tenant_id BIGINT,
                partition_key VARCHAR(128) NOT NULL,
                occurred_at TIMESTAMP NOT NULL,
                correlation_id VARCHAR(128) NOT NULL,
                causation_id VARCHAR(128),
                traceparent VARCHAR(128),
                tracestate VARCHAR(512),
                payload CLOB NOT NULL,
                status VARCHAR(20) NOT NULL,
                available_at TIMESTAMP NOT NULL,
                attempt_count INTEGER NOT NULL,
                claim_owner VARCHAR(128),
                claim_until TIMESTAMP,
                published_at TIMESTAMP,
                last_error_code VARCHAR(128),
                created_at TIMESTAMP NOT NULL
            )
            """);
    }

    @Test
    void twoRelayOwnersCannotClaimSameActiveLease() {
        insert("m-1", "PENDING", 0, NOW.minusSeconds(10), null);

        List<CanonicalOutboxMessage> first = store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);
        List<CanonicalOutboxMessage> second = store.claimDue("relay-b", NOW.plusSeconds(1), Duration.ofSeconds(30), 10);

        assertEquals(1, first.size());
        assertTrue(second.isEmpty());
        assertEquals("relay-a", queryString("claim_owner", "m-1"));
        assertEquals("IN_FLIGHT", queryString("status", "m-1"));
        assertEquals(1, first.get(0).attemptCount());
    }

    @Test
    void expiredLeaseCanBeReclaimedByAnotherRelay() {
        insert("m-2", "IN_FLIGHT", 1, NOW.minusSeconds(10), NOW.minusSeconds(1));
        jdbcTemplate.update("UPDATE platform_tenant_outbox SET claim_owner = ? WHERE message_id = ?", "relay-a", "m-2");

        List<CanonicalOutboxMessage> claimed = store.claimDue("relay-b", NOW, Duration.ofSeconds(30), 10);

        assertEquals(1, claimed.size());
        assertEquals("relay-b", queryString("claim_owner", "m-2"));
        assertEquals(2, claimed.get(0).attemptCount());
    }

    @Test
    void markPublishedRequiresOwnedInFlightClaim() {
        insert("m-3", "PENDING", 0, NOW.minusSeconds(10), null);

        assertThrows(IllegalStateException.class, () -> store.markPublished("relay-a", "m-3", NOW));

        store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);
        store.markPublished("relay-a", "m-3", NOW.plusSeconds(1));

        assertEquals("PUBLISHED", queryString("status", "m-3"));
        assertEquals(null, queryString("last_error_code", "m-3"));
    }

    @Test
    void retryAndParkingClearActiveLease() {
        insert("m-4", "PENDING", 0, NOW.minusSeconds(10), null);
        insert("m-5", "PENDING", 0, NOW.minusSeconds(10), null);

        store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);
        store.releaseForRetry("relay-a", "m-4", NOW.plusSeconds(5), "KAFKA_TIMEOUT");
        store.park("relay-a", "m-5", "SCHEMA_INCOMPATIBLE", NOW.plusSeconds(1));

        assertEquals("PENDING", queryString("status", "m-4"));
        assertEquals("KAFKA_TIMEOUT", queryString("last_error_code", "m-4"));
        assertEquals(null, queryString("claim_owner", "m-4"));
        assertEquals("PARKED", queryString("status", "m-5"));
        assertEquals("SCHEMA_INCOMPATIBLE", queryString("last_error_code", "m-5"));
        assertEquals(null, queryString("claim_owner", "m-5"));
    }

    @Test
    void backlogSnapshotReportsPendingInFlightParkedAndOldestAge() {
        insert("m-6", "PENDING", 0, NOW.minusSeconds(60), null);
        insert("m-7", "IN_FLIGHT", 1, NOW.minusSeconds(20), NOW.plusSeconds(30));
        insert("m-8", "PARKED", 3, NOW.minusSeconds(10), null);

        OutboxBacklogSnapshot snapshot = store.readBacklog(NOW);

        assertEquals(1, snapshot.pendingCount());
        assertEquals(1, snapshot.inFlightCount());
        assertEquals(1, snapshot.parkedCount());
        assertEquals(Duration.ofSeconds(60), snapshot.oldestPendingAge());
    }

    private void insert(
            String id,
            String status,
            int attemptCount,
            OffsetDateTime availableAt,
            OffsetDateTime claimUntil) {
        jdbcTemplate.update("""
            INSERT INTO platform_tenant_outbox (
                message_id, event_id, message_kind, message_type, schema_version,
                reliability, producer_plugin_id, scope, tenant_id, partition_key,
                occurred_at, correlation_id, payload, status, available_at,
                attempt_count, claim_until, created_at
            ) VALUES (?, ?, 'EVENT', 'TenantFirstOwnerAccepted', '1.0.0',
                'CRITICAL', 'platform-tenant', 'TENANT', 100, '100',
                ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            "event-" + id,
            timestamp(NOW.minusSeconds(60)),
            "corr-" + id,
            "{\"tenantId\":100}",
            status,
            timestamp(availableAt),
            attemptCount,
            claimUntil == null ? null : timestamp(claimUntil),
            timestamp(NOW.minusSeconds(60)));
    }

    private String queryString(String column, String messageId) {
        return jdbcTemplate.query(
            "SELECT " + column + " FROM platform_tenant_outbox WHERE message_id = ?",
            rs -> rs.next() ? rs.getString(1) : null,
            messageId);
    }

    private static java.sql.Timestamp timestamp(OffsetDateTime value) {
        return java.sql.Timestamp.from(value.toInstant().atZone(ZoneOffset.UTC).toInstant());
    }
}
