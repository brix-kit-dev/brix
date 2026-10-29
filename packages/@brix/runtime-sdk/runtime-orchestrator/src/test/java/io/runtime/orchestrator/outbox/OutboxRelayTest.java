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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.conformance.RecordingMessageTracing;

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
    void aMessageIsSentWithTheTraceContextOfItsProducerSpan() {
        // Root BRX-RS-OTEL-002: the relay sends each message in a PRODUCER span, a child of the context it carries.
        InMemoryStore store = new InMemoryStore();
        store.add("m-1", 0);
        AtomicReference<CanonicalOutboxMessage> published = new AtomicReference<>();
        RecordingMessageTracing tracing = new RecordingMessageTracing();
        OutboxRelay relay = new OutboxRelay("relay-a", store, published::set, POLICY, CLOCK, ignored -> Duration.ZERO,
            OutboxRelayMetrics.NOOP, tracing);

        relay.processDueBatch();

        RecordingMessageTracing.Recorded span = tracing.spans(RecordingMessageTracing.PRODUCER).get(0);
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", span.parent());
        assertEquals(span.traceparent(), published.get().traceparent());
        assertFalse(span.failed());
        assertEquals("PUBLISHED", store.status("m-1"));
    }

    @Test
    void aFailedSendMarksItsProducerSpanFailed() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-2", 0);
        RecordingMessageTracing tracing = new RecordingMessageTracing();
        OutboxRelay relay = new OutboxRelay("relay-a", store, ignored -> {
            throw new OutboxTransportException("KAFKA_TIMEOUT", "timeout", null);
        }, POLICY, CLOCK, ignored -> Duration.ZERO, OutboxRelayMetrics.NOOP, tracing);

        relay.processDueBatch();

        assertTrue(tracing.spans(RecordingMessageTracing.PRODUCER).get(0).failed());
        assertEquals("PENDING", store.status("m-2"));
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
    void aFailedMessageHoldsBackTheLaterMessagesOfItsKeyWithoutSpendingTheirAttempts() {
        // Root BRX-RS-MESSAGE-027: the messages of one partition key leave in their order.
        InMemoryStore store = new InMemoryStore();
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        store.add("m-a", "key-1", now.minusSeconds(3));
        store.add("m-b", "key-1", now.minusSeconds(2));
        store.add("m-c", "key-2", now.minusSeconds(1));
        List<String> published = new ArrayList<>();
        OutboxRelay relay = relay(store, message -> {
            if (message.messageId().equals("m-a")) {
                throw new OutboxTransportException("KAFKA_TIMEOUT", "timeout", null);
            }
            published.add(message.messageId());
        });

        OutboxRelayRunResult result = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(3, 1, 1, 0), result);
        assertEquals(List.of("m-c"), published, "the other key goes on");
        assertEquals("PENDING", store.status("m-a"));
        assertEquals("PENDING", store.status("m-b"));
        assertEquals(0, store.attempts("m-b"), "m-b was handed back untried");
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
    void aTransportThatIsNotReadyGetsNothingAndCostsNoAttempt() {
        InMemoryStore store = new InMemoryStore();
        store.add("m-7", 0);
        AtomicBoolean ready = new AtomicBoolean();
        AtomicReference<CanonicalOutboxMessage> published = new AtomicReference<>();
        OutboxRelay relay = relay(store, new OutboxTransport() {
            @Override
            public void publish(CanonicalOutboxMessage message) {
                published.set(message);
            }

            @Override
            public boolean ready() {
                return ready.get();
            }
        });

        OutboxRelayRunResult waiting = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(0, 0, 0, 0), waiting);
        assertEquals("PENDING", store.status("m-7"));
        assertEquals(null, store.claimOwner("m-7"));

        ready.set(true);
        OutboxRelayRunResult delivered = relay.processDueBatch();

        assertEquals(new OutboxRelayRunResult(1, 1, 0, 0), delivered);
        assertEquals(1, published.get().attemptCount());
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

    private static CanonicalOutboxMessage message(String id, int attemptCount, String partitionKey,
            OffsetDateTime occurredAt) {
        CanonicalOutboxMessage message = message(id, attemptCount);
        return new CanonicalOutboxMessage(message.messageId(), message.eventId(), message.messageKind(),
            message.messageType(), message.schemaVersion(), message.reliability(), message.producerPluginId(),
            message.scope(), message.tenantId(), partitionKey, occurredAt, message.correlationId(),
            message.causationId(), message.traceparent(), message.tracestate(), message.payload(), attemptCount);
    }

    private static CanonicalOutboxMessage attempt(CanonicalOutboxMessage message, int attemptCount) {
        return new CanonicalOutboxMessage(message.messageId(), message.eventId(), message.messageKind(),
            message.messageType(), message.schemaVersion(), message.reliability(), message.producerPluginId(),
            message.scope(), message.tenantId(), message.partitionKey(), message.occurredAt(), message.correlationId(),
            message.causationId(), message.traceparent(), message.tracestate(), message.payload(), attemptCount);
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

        void add(String id, String partitionKey, OffsetDateTime occurredAt) {
            records.put(id, new StoredMessage(message(id, 0, partitionKey, occurredAt)));
        }

        int attempts(String id) {
            return records.get(id).message.attemptCount();
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
                .sorted(Comparator.comparing((StoredMessage record) -> record.message.occurredAt())
                    .thenComparing(record -> record.message.messageId()))
                .limit(batchSize)
                .toList();
            List<CanonicalOutboxMessage> claimed = new ArrayList<>();
            for (StoredMessage record : due) {
                record.status = "IN_FLIGHT";
                record.claimOwner = relayOwner;
                record.claimUntil = now.plus(leaseDuration);
                record.message = attempt(record.message, record.message.attemptCount() + 1);
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
        public void releaseUntried(String relayOwner, String messageId) {
            StoredMessage record = requireOwned(relayOwner, messageId);
            record.status = "PENDING";
            record.claimOwner = null;
            record.message = attempt(record.message, Math.max(0, record.message.attemptCount() - 1));
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

        @Override
        public List<ParkedOutboxMessage> listParked(int limit) {
            return List.of();
        }

        @Override
        public boolean replayParked(String eventId, OffsetDateTime now) {
            return false;
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
