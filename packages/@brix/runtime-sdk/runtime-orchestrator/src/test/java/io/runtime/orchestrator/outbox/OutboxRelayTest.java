/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class OutboxRelayTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC);
    private static final OutboxRelayPolicy POLICY = new OutboxRelayPolicy(
        10,
        3,
        Duration.ofSeconds(30),
        Duration.ofSeconds(5),
        Duration.ZERO);

    @Test
    void publishesClaimedMessageAndFinalizesPublishedStatus() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-1", 0);
        AtomicReference<CanonicalOutboxMessage> published = new AtomicReference<>();
        OutboxRelay relay = relay(store, published::set);

        OutboxRelayRunResult result = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(1, 1, 0, 0), result);
        assertEquals("PUBLISHED", store.status("m-1"));
        assertEquals("relay-a", store.claimOwner("m-1"));
        assertSame(published.get(), store.claimedMessage("m-1"));
        assertEquals("event-m-1", published.get().eventId());
    }

    @Test
    void transientTransportFailureReleasesForBackoffRetry() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-2", 0);
        OutboxRelay relay = relay(store, ignored -> {
            throw new OutboxTransportException("KAFKA_TIMEOUT", "timeout", null);
        });

        OutboxRelayRunResult result = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(1, 0, 1, 0), result);
        assertEquals("PENDING", store.status("m-2"));
        assertEquals("KAFKA_TIMEOUT", store.error("m-2"));
        assertFalse(store.availableAt("m-2").isBefore(OffsetDateTime.now(CLOCK).plusSeconds(5)));
    }

    @Test
    void retryBudgetExhaustedParksMessageWithStableError() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-3", 2);
        OutboxRelay relay = relay(store, ignored -> {
            throw new OutboxTransportException("KAFKA_TIMEOUT", "timeout", null);
        });

        OutboxRelayRunResult result = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(1, 0, 0, 1), result);
        assertEquals("PARKED", store.status("m-3"));
        assertEquals("KAFKA_TIMEOUT", store.error("m-3"));
    }

    @Test
    void permanentTransportFailureParksBeforeRetryBudget() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-4", 0);
        OutboxRelay relay = relay(store, ignored -> {
            throw new OutboxTransportException("SCHEMA_INCOMPATIBLE", "bad schema", null, true);
        });

        OutboxRelayRunResult result = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(1, 0, 0, 1), result);
        assertEquals("PARKED", store.status("m-4"));
        assertEquals("SCHEMA_INCOMPATIBLE", store.error("m-4"));
    }

    @Test
    void drainStopsNewClaims() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-5", 0);
        OutboxRelay relay = relay(store, ignored -> {
        });

        relay.drain();
        OutboxRelayRunResult result = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(0, 0, 0, 0), result);
        assertEquals("PENDING", store.status("m-5"));
    }

    @Test
    void secondRelayCannotClaimMessageWithActiveLease() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-6", 0);

        List<CanonicalOutboxMessage> firstClaim =
            store.claimDue("relay-a", OffsetDateTime.now(CLOCK), Duration.ofSeconds(30), 1);
        List<CanonicalOutboxMessage> secondClaim =
            store.claimDue("relay-b", OffsetDateTime.now(CLOCK).plusSeconds(1), Duration.ofSeconds(30), 1);

        assertEquals(1, firstClaim.size());
        assertTrue(secondClaim.isEmpty());
        assertEquals("relay-a", store.claimOwner("m-6"));
    }

    private static OutboxRelay relay(InMemoryStore store, OutboxTransport transport) {
        return new OutboxRelay("relay-a", store, transport, POLICY, CLOCK, ignored -> Duration.ZERO, OutboxRelayMetrics.NOOP);
    }

    private static CanonicalOutboxMessage message(String id, int attemptCount) {
        return new CanonicalOutboxMessage(
            id,
            "event-" + id,
            "EVENT",
            "TenantFirstOwnerAccepted",
            "1.0.0",
            "CRITICAL",
            "platform-tenant",
            "TENANT",
            100L,
            "100",
            OffsetDateTime.now(CLOCK),
            "corr-" + id,
            null,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            null,
            "{\"tenantId\":100}",
            attemptCount);
    }

    private static final class InMemoryStore implements OutboxMessageStore {

        private final Map<String, StoredMessage> records = new HashMap<>();

        void add(String id, int attemptCount) {
            records.put(id, new StoredMessage(message(id, attemptCount)));
        }

        String status(String id) {
            return records.get(id).status;
        }

        String claimOwner(String id) {
            return records.get(id).claimOwner;
        }

        String error(String id) {
            return records.get(id).lastErrorCode;
        }

        OffsetDateTime availableAt(String id) {
            return records.get(id).availableAt;
        }

        CanonicalOutboxMessage claimedMessage(String id) {
            return records.get(id).claimedMessage;
        }

        @Override
        public void append(CanonicalOutboxMessage message) {
            records.put(message.messageId(), new StoredMessage(message));
        }

        @Override
        public List<CanonicalOutboxMessage> claimDue(
                String relayOwner,
                OffsetDateTime now,
                Duration leaseDuration,
                int batchSize) {
            List<StoredMessage> due = records.values().stream()
                .filter(record -> ("PENDING".equals(record.status) && !record.availableAt.isAfter(now))
                    || ("IN_FLIGHT".equals(record.status) && record.claimUntil.isBefore(now)))
                .sorted(Comparator.comparing(record -> record.message.occurredAt()))
                .limit(batchSize)
                .toList();
            List<CanonicalOutboxMessage> claimed = new ArrayList<>();
            for (StoredMessage record : due) {
                record.status = "IN_FLIGHT";
                record.claimOwner = relayOwner;
                record.claimUntil = now.plus(leaseDuration);
                record.message = message(record.message.messageId(), record.message.attemptCount() + 1);
                record.claimedMessage = record.message;
                claimed.add(record.message);
            }
            return claimed;
        }

        @Override
        public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
            StoredMessage record = requireOwned(relayOwner, messageId);
            record.status = "PUBLISHED";
            record.publishedAt = publishedAt;
        }

        @Override
        public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
            StoredMessage record = requireOwned(relayOwner, messageId);
            record.status = "PENDING";
            record.availableAt = availableAt;
            record.lastErrorCode = errorCode;
        }

        @Override
        public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
            StoredMessage record = requireOwned(relayOwner, messageId);
            record.status = "PARKED";
            record.lastErrorCode = errorCode;
        }

        @Override
        public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
            long pending = records.values().stream().filter(record -> "PENDING".equals(record.status)).count();
            long inFlight = records.values().stream().filter(record -> "IN_FLIGHT".equals(record.status)).count();
            long parked = records.values().stream().filter(record -> "PARKED".equals(record.status)).count();
            return new OutboxBacklogSnapshot(pending, inFlight, parked, Duration.ZERO);
        }

        private StoredMessage requireOwned(String relayOwner, String messageId) {
            StoredMessage record = records.get(messageId);
            if (record == null || !relayOwner.equals(record.claimOwner) || !"IN_FLIGHT".equals(record.status)) {
                throw new IllegalStateException("message is not owned by relay");
            }
            return record;
        }
    }

    private static final class StoredMessage {

        private CanonicalOutboxMessage message;
        private CanonicalOutboxMessage claimedMessage;
        private String status = "PENDING";
        private String claimOwner;
        private OffsetDateTime claimUntil = OffsetDateTime.MIN;
        private OffsetDateTime availableAt = OffsetDateTime.now(CLOCK);
        private OffsetDateTime publishedAt;
        private String lastErrorCode;

        StoredMessage(CanonicalOutboxMessage message) {
            this.message = message;
        }
    }
}
