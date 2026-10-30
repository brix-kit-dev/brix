/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxBacklogSnapshot;
import io.runtime.orchestrator.outbox.ParkedOutboxMessage;

/**
 * The JDBC outbox store, on H2 in PostgreSQL mode, or on PostgreSQL itself when {@code BRIX_P0_POSTGRES_URL},
 * {@code BRIX_P0_POSTGRES_USER} and {@code BRIX_P0_POSTGRES_PASSWORD} point at a throwaway database; each test then
 * works in a table of its own and drops it.
 */
class JdbcOutboxMessageStoreTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-28T01:00:00Z");

    private JdbcTemplate jdbcTemplate;
    private JdbcOutboxMessageStore store;
    private String table;

    @BeforeEach
    void setUp() {
        String postgresUrl = System.getenv("BRIX_P0_POSTGRES_URL");
        DriverManagerDataSource dataSource = postgresUrl == null || postgresUrl.isBlank()
            ? new DriverManagerDataSource(
                "jdbc:h2:mem:outbox_" + System.nanoTime()
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "")
            : new DriverManagerDataSource(postgresUrl, System.getenv("BRIX_P0_POSTGRES_USER"),
                System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        org.springframework.jdbc.datasource.DataSourceTransactionManager transactionManager =
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        table = "outbox_test_" + Long.toHexString(System.nanoTime());
        store = new JdbcOutboxMessageStore(jdbcTemplate, new TransactionTemplate(transactionManager), table);
        jdbcTemplate.execute("""
            CREATE TABLE %s (
                message_id VARCHAR(64) PRIMARY KEY,
                seq BIGINT GENERATED ALWAYS AS IDENTITY,
                event_id VARCHAR(64) NOT NULL,
                message_kind VARCHAR(20) NOT NULL,
                message_type VARCHAR(128) NOT NULL,
                event_type VARCHAR(128) NOT NULL,
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
                payload TEXT NOT NULL,
                status VARCHAR(20) NOT NULL,
                available_at TIMESTAMP NOT NULL,
                attempt_count INTEGER NOT NULL,
                claim_owner VARCHAR(128),
                claim_until TIMESTAMP,
                published_at TIMESTAMP,
                last_error_code VARCHAR(128),
                created_at TIMESTAMP NOT NULL
            )
            """.formatted(table));
        jdbcTemplate.execute("CREATE INDEX " + table + "_key ON " + table + " (partition_key, seq)");
    }

    @AfterEach
    void dropTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
    }

    @Test
    void aMessageWaitsBehindAnEarlierMessageOfItsKeyThatWaitsForItsRetry() {
        // Root BRX-RS-MESSAGE-027: the events of one partition key leave in their order.
        insert("a", "PENDING", 1, NOW.plusSeconds(30), null, "key-1", NOW.minusSeconds(30));
        insert("b", "PENDING", 0, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(20));
        insert("c", "PENDING", 0, NOW.minusSeconds(1), null, "key-2", NOW.minusSeconds(10));

        List<CanonicalOutboxMessage> claimed = store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);

        assertEquals(List.of("c"), claimed.stream().map(CanonicalOutboxMessage::messageId).toList());
    }

    @Test
    void aMessageWaitsBehindAnEarlierMessageOfItsKeyThatIsInFlight() {
        insert("a", "IN_FLIGHT", 1, NOW.minusSeconds(5), NOW.plusSeconds(20), "key-1", NOW.minusSeconds(30));
        insert("b", "PENDING", 0, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(20));

        assertEquals(List.of(), store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10));
    }

    @Test
    void dueMessagesOfOneKeyAreClaimedTogetherInTheOrderTheOutboxRecordedThem() {
        // Kafka design §14 K13: the outbox numbers its messages; the producers' clocks do not decide.
        insert("a", "PENDING", 0, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(20));
        insert("b", "PENDING", 0, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(30));

        List<CanonicalOutboxMessage> claimed = store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);

        assertEquals(List.of("a", "b"), claimed.stream().map(CanonicalOutboxMessage::messageId).toList());
    }

    @Test
    void aRelayDoesNotTakeTheLaterMessageOfAKeyWhileAnotherRelayClaimsTheEarlierOne() throws Exception {
        // Kafka design §14 K12: relay x has claimed "a" but not committed yet; relay b reads "a" as pending, so its
        // candidates are "a" and "b". It must not take "b": "b" would leave before "a".
        assumeTrue(System.getenv("BRIX_P0_POSTGRES_URL") != null, "needs PostgreSQL's row locks and re-checks");
        insert("a", "PENDING", 0, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(20));
        insert("b", "PENDING", 0, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(10));
        DataSource dataSource = jdbcTemplate.getDataSource();
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement claim = other.prepareStatement("UPDATE " + table
                    + " SET status = 'IN_FLIGHT', claim_owner = 'relay-x', claim_until = ?,"
                    + " attempt_count = attempt_count + 1 WHERE message_id = 'a'")) {
                claim.setTimestamp(1, timestamp(NOW.plusSeconds(30)));
                assertEquals(1, claim.executeUpdate());
            }
            Thread committer = new Thread(() -> {
                try {
                    Thread.sleep(500);
                    other.commit();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            committer.start();

            List<CanonicalOutboxMessage> claimed = store.claimDue("relay-b", NOW, Duration.ofSeconds(30), 10);

            committer.join();
            assertEquals(List.of(), claimed);
        }
        assertEquals("relay-x", queryString("claim_owner", "a"));
        assertEquals("PENDING", queryString("status", "b"));
        assertEquals("0", queryString("attempt_count", "b"));
    }

    @Test
    void anUnreadableRowIsParkedInsteadOfStoppingTheOutbox() {
        insert("bad", "PENDING", 0, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(20));
        jdbcTemplate.update("UPDATE " + table + " SET correlation_id = '' WHERE message_id = 'bad'");
        insert("good", "PENDING", 0, NOW.minusSeconds(1), null, "key-2", NOW.minusSeconds(10));

        List<CanonicalOutboxMessage> claimed = store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);

        assertEquals(List.of("good"), claimed.stream().map(CanonicalOutboxMessage::messageId).toList());
        assertEquals("PARKED", queryString("status", "bad"));
        assertEquals(JdbcOutboxMessageStore.ERROR_INVALID_ROW, queryString("last_error_code", "bad"));
        assertEquals(null, queryString("claim_owner", "bad"));
    }

    @Test
    void aMessageHandedBackUntriedKeepsItsAttemptBudget() {
        insert("a", "PENDING", 2, NOW.minusSeconds(1), null, "key-1", NOW.minusSeconds(30));
        store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);
        assertEquals("3", queryString("attempt_count", "a"));

        store.releaseUntried("relay-a", "a");

        assertEquals("PENDING", queryString("status", "a"));
        assertEquals("2", queryString("attempt_count", "a"));
        assertEquals(null, queryString("claim_owner", "a"));
        assertThrows(IllegalStateException.class, () -> store.releaseUntried("relay-a", "a"),
            "only a claimed message is handed back");
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
        jdbcTemplate.update("UPDATE " + table + " SET claim_owner = ? WHERE message_id = ?", "relay-a", "m-2");

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
    void aParkedMessageIsListedWithItsLastErrorAndTheTimeItWasParked() {
        insert("m-9", "PENDING", 0, NOW.minusSeconds(10), null);
        insert("m-10", "PUBLISHED", 1, NOW.minusSeconds(10), null);
        store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);
        store.park("relay-a", "m-9", "EVENT_PAYLOAD_INVALID", NOW.plusSeconds(1));

        List<ParkedOutboxMessage> parked = store.listParked(10);

        assertEquals(List.of(new ParkedOutboxMessage("event-m-9", "m-9", "TenantFirstOwnerAccepted", "1.0.0",
            "platform-tenant", 100L, 1, "EVENT_PAYLOAD_INVALID", NOW.minusSeconds(60), NOW.plusSeconds(1))), parked);
    }

    @Test
    void aReplayedMessageIsDueAgainWithAFreshAttemptBudgetAndTheSameEventId() {
        insert("m-11", "PARKED", 8, NOW.minusSeconds(10), null);

        assertTrue(store.replayParked("event-m-11", NOW));

        assertEquals("PENDING", queryString("status", "m-11"));
        assertEquals("0", queryString("attempt_count", "m-11"));
        assertEquals(null, queryString("last_error_code", "m-11"));
        List<CanonicalOutboxMessage> claimed = store.claimDue("relay-a", NOW, Duration.ofSeconds(30), 10);
        assertEquals(List.of("event-m-11"), claimed.stream().map(CanonicalOutboxMessage::eventId).toList());
        assertEquals(1, claimed.get(0).attemptCount());
    }

    @Test
    void onlyAParkedMessageCanBeReplayed() {
        insert("m-12", "PENDING", 2, NOW.minusSeconds(10), null);

        assertFalse(store.replayParked("event-m-12", NOW));
        assertFalse(store.replayParked("event-unknown", NOW));
        assertEquals("2", queryString("attempt_count", "m-12"));
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
        insert(id, status, attemptCount, availableAt, claimUntil, "100", NOW.minusSeconds(60));
    }

    private void insert(
            String id,
            String status,
            int attemptCount,
            OffsetDateTime availableAt,
            OffsetDateTime claimUntil,
            String partitionKey,
            OffsetDateTime occurredAt) {
        jdbcTemplate.update("""
            INSERT INTO %s (
                message_id, event_id, message_kind, message_type, event_type, schema_version,
                reliability, producer_plugin_id, scope, tenant_id, partition_key,
                occurred_at, correlation_id, payload, status, available_at,
                attempt_count, claim_until, created_at
            ) VALUES (?, ?, 'EVENT', 'TenantFirstOwnerAccepted', 'TenantFirstOwnerAccepted', '1.0.0',
                'CRITICAL', 'platform-tenant', 'TENANT', 100, ?,
                ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(table),
            id,
            "event-" + id,
            partitionKey,
            timestamp(occurredAt),
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
            "SELECT " + column + " FROM " + table + " WHERE message_id = ?",
            rs -> rs.next() ? rs.getString(1) : null,
            messageId);
    }

    private static java.sql.Timestamp timestamp(OffsetDateTime value) {
        return java.sql.Timestamp.from(value.toInstant().atZone(ZoneOffset.UTC).toInstant());
    }
}
