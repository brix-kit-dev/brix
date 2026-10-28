/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

/**
 * The management endpoint lists each outbox's parked messages and replays one by its event id.
 */
class OutboxEndpointTest {

    private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");

    private final ParkedStore tenant = new ParkedStore();
    private final ParkedStore identity = new ParkedStore();
    private final OutboxEndpoint endpoint = new OutboxEndpoint(
        () -> () -> Map.of("platform_tenant_outbox", tenant, "platform_identity_outbox", identity),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsTheParkedMessagesOfEveryOutbox() {
        tenant.parked.add(parked("event-1"));

        OutboxEndpoint.ParkedMessages parked = endpoint.parked();

        assertEquals(List.of("platform_identity_outbox", "platform_tenant_outbox"), List.copyOf(parked.outboxes().keySet()));
        assertEquals(List.of(parked("event-1")), parked.outboxes().get("platform_tenant_outbox"));
        assertEquals(List.of(), parked.outboxes().get("platform_identity_outbox"));
        assertEquals(OutboxEndpoint.LIST_LIMIT, tenant.limit);
    }

    @Test
    void replaysTheParkedMessageInTheOutboxThatHoldsIt() {
        tenant.parked.add(parked("event-1"));

        WebEndpointResponse<OutboxEndpoint.Replay> replay = endpoint.replay("event-1");

        assertEquals(WebEndpointResponse.STATUS_OK, replay.getStatus());
        assertEquals(new OutboxEndpoint.Replay("platform_tenant_outbox", "event-1", true), replay.getBody());
        assertEquals(List.of("event-1@" + NOW.atOffset(ZoneOffset.UTC)), tenant.replayed);
        assertTrue(tenant.parked.isEmpty());
    }

    @Test
    void anEventIdNoOutboxHoldsParkedIsNotFound() {
        WebEndpointResponse<OutboxEndpoint.Replay> replay = endpoint.replay("event-unknown");

        assertEquals(WebEndpointResponse.STATUS_NOT_FOUND, replay.getStatus());
        assertFalse(replay.getBody().replayed());
    }

    @Test
    void withoutOutboxesThereIsNothingToList() {
        OutboxEndpoint none = new OutboxEndpoint(() -> null, Clock.systemUTC());

        assertEquals(Map.of(), none.parked().outboxes());
        assertEquals(WebEndpointResponse.STATUS_NOT_FOUND, none.replay("event-1").getStatus());
    }

    private static ParkedOutboxMessage parked(String eventId) {
        OffsetDateTime occurred = OffsetDateTime.parse("2026-10-28T08:00:00Z");
        return new ParkedOutboxMessage(eventId, eventId, "TenantFirstOwnerAccepted", "1.0.0", "platform-tenant", 910001L,
            8, "EVENT_HANDLER_FAILED", occurred, occurred.plusMinutes(5));
    }

    private static final class ParkedStore implements OutboxMessageStore {

        private final List<ParkedOutboxMessage> parked = new ArrayList<>();
        private final List<String> replayed = new ArrayList<>();
        private int limit;

        @Override
        public List<ParkedOutboxMessage> listParked(int limit) {
            this.limit = limit;
            return List.copyOf(parked);
        }

        @Override
        public boolean replayParked(String eventId, OffsetDateTime now) {
            boolean found = parked.removeIf(message -> message.eventId().equals(eventId));
            if (found) {
                replayed.add(eventId + "@" + now);
            }
            return found;
        }

        @Override
        public void append(CanonicalOutboxMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CanonicalOutboxMessage> claimDue(String relayOwner, OffsetDateTime now, Duration lease, int batchSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
            throw new UnsupportedOperationException();
        }
    }
}
