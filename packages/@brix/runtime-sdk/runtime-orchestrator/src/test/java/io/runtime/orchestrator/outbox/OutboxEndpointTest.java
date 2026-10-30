/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

import io.runtime.orchestrator.event.conformance.RecordingMessageAudit;
import io.runtime.orchestrator.outbox.MessageOperationAudit.MessageOperation;

class OutboxEndpointTest {

    private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");
    private static final OffsetDateTime AT = NOW.atOffset(ZoneOffset.UTC);
    private static final Duration RETENTION = Duration.ofDays(14);
    private static final String TENANT = "platform_tenant_outbox";

    private final Store tenant = new Store();
    private final Store identity = new Store();
    private final List<String> happened = new ArrayList<>();
    private final RecordingMessageAudit audit = new RecordingMessageAudit(
        operation -> happened.add("audited " + operation.action() + (operation.errorCode() == null ? ""
            : " failed " + operation.errorCode())));
    private final OutboxEndpoint endpoint = new OutboxEndpoint(
        () -> () -> Map.of(TENANT, tenant, "platform_identity_outbox", identity),
        () -> audit,
        RETENTION,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsTheParkedMessagesOfEveryOutbox() {
        tenant.park("event-1");

        OutboxEndpoint.ParkedMessages parked = endpoint.parked();

        assertEquals(List.of("platform_identity_outbox", TENANT), List.copyOf(parked.outboxes().keySet()));
        assertEquals(List.of(parked("event-1")), parked.outboxes().get(TENANT));
        assertEquals(List.of(), parked.outboxes().get("platform_identity_outbox"));
        assertEquals(OutboxEndpoint.LIST_LIMIT, tenant.limit);
    }

    @Test
    void aReplayIsAuditedWithTheOperatorAndTheReasonBeforeItTakesEffect() {
        tenant.park("event-1");

        WebEndpointResponse<OutboxEndpoint.Outcome> replay = endpoint.replay("event-1", "ops-alice", "broker fixed",
            SecurityContext.NONE);

        assertEquals(WebEndpointResponse.STATUS_OK, replay.getStatus());
        assertEquals(new OutboxEndpoint.Outcome(TENANT, "event-1", true, null), replay.getBody());
        assertEquals(List.of("audited MESSAGE_REPLAYED", "replayed event-1@" + AT), happened);
        assertEquals(List.of(MessageOperation.of(MessageOperationAudit.REPLAYED, "event-1", TENANT, "ops-alice", null,
            "broker fixed")), audit.recorded());
        assertEquals("PENDING", tenant.status("event-1"));
    }

    @Test
    void theAuditKeepsWhomTheManagementPortAuthenticated() {
        tenant.park("event-1");
        Principal principal = () -> "svc-operator";

        endpoint.replay("event-1", "ops-alice", "broker fixed", new SecurityContext() {
            @Override
            public Principal getPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }
        });

        assertEquals("svc-operator", audit.recorded().get(0).principal());
        assertEquals("ops-alice", audit.recorded().get(0).operator());
    }

    @Test
    void aDiscardedMessageIsNeverSent() {
        tenant.park("event-1");

        WebEndpointResponse<OutboxEndpoint.Outcome> discard = endpoint.discard("event-1", "ops-alice",
            "payload rejected by every subscriber", SecurityContext.NONE);

        assertEquals(WebEndpointResponse.STATUS_OK, discard.getStatus());
        assertEquals(List.of("audited MESSAGE_DISCARDED", "discarded event-1@" + AT), happened);
        assertEquals("DISCARDED", tenant.status("event-1"));
    }

    @Test
    void aPublishedMessageIsRepublishedWithinTheRetention() {
        tenant.publish("event-1", AT.minusDays(13));

        WebEndpointResponse<OutboxEndpoint.Outcome> republish = endpoint.republish("event-1", OutboxEndpoint.REPUBLISH,
            "ops-alice", "new subscription needs history", SecurityContext.NONE);

        assertEquals(WebEndpointResponse.STATUS_OK, republish.getStatus());
        assertEquals(List.of("audited MESSAGE_REPUBLISHED", "republished event-1@" + AT + " since " + AT.minus(RETENTION)),
            happened);
        assertEquals("PENDING", tenant.status("event-1"));
    }

    @Test
    void aMessagePublishedBeforeTheRetentionIsNotRepublished() {
        tenant.publish("event-1", AT.minusDays(15));

        WebEndpointResponse<OutboxEndpoint.Outcome> republish = endpoint.republish("event-1", OutboxEndpoint.REPUBLISH,
            "ops-alice", "late backfill", SecurityContext.NONE);

        assertEquals(OutboxEndpoint.STATUS_CONFLICT, republish.getStatus());
        assertEquals(OutboxEndpoint.RETENTION_EXCEEDED, republish.getBody().errorCode());
        assertEquals(List.of(), happened);
    }

    @Test
    void anUnknownActionIsABadRequest() {
        tenant.publish("event-1", AT.minusDays(1));

        assertEquals(OutboxEndpoint.STATUS_BAD_REQUEST, endpoint.republish("event-1", "resend", "ops-alice", "why",
            SecurityContext.NONE).getStatus());
        assertEquals(List.of(), happened);
    }

    @Test
    void anActionNeedsAnOperatorAndAReason() {
        tenant.park("event-1");

        assertEquals(OutboxEndpoint.STATUS_BAD_REQUEST, endpoint.replay("event-1", " ", "why", SecurityContext.NONE)
            .getStatus());
        assertEquals(OutboxEndpoint.STATUS_BAD_REQUEST, endpoint.discard("event-1", "ops-alice", "", SecurityContext.NONE)
            .getStatus());
        assertEquals(List.of(), happened);
        assertEquals("PARKED", tenant.status("event-1"));
    }

    @Test
    void onlyAParkedMessageIsReplayedOrDiscarded() {
        tenant.publish("event-1", AT.minusDays(1));

        WebEndpointResponse<OutboxEndpoint.Outcome> replay = endpoint.replay("event-1", "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(OutboxEndpoint.STATUS_CONFLICT, replay.getStatus());
        assertEquals(OutboxEndpoint.WRONG_STATE, replay.getBody().errorCode());
        assertEquals(List.of(), happened);
    }

    @Test
    void anEventIdNoOutboxHoldsIsNotFound() {
        WebEndpointResponse<OutboxEndpoint.Outcome> replay = endpoint.replay("event-unknown", "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(WebEndpointResponse.STATUS_NOT_FOUND, replay.getStatus());
        assertFalse(replay.getBody().done());
        assertNull(replay.getBody().outbox());
        assertEquals(List.of(), audit.recorded());
    }

    @Test
    void anActionThePlatformAuditCannotRecordIsRefused() {
        tenant.park("event-1");
        audit.refusing(true);

        WebEndpointResponse<OutboxEndpoint.Outcome> replay = endpoint.replay("event-1", "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(OutboxEndpoint.STATUS_UNAVAILABLE, replay.getStatus());
        assertEquals(OutboxEndpoint.AUDIT_UNAVAILABLE, replay.getBody().errorCode());
        assertEquals("PARKED", tenant.status("event-1"));
    }

    @Test
    void withoutAPlatformAuditEveryActionIsRefused() {
        tenant.park("event-1");
        OutboxEndpoint unaudited = new OutboxEndpoint(() -> () -> Map.of(TENANT, tenant), () -> null, RETENTION,
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(OutboxEndpoint.STATUS_UNAVAILABLE, unaudited.discard("event-1", "ops-alice", "why",
            SecurityContext.NONE).getStatus());
        assertEquals("PARKED", tenant.status("event-1"));
    }

    @Test
    void aMessageThatChangedAfterTheAuditIsRecordedAsAFailure() {
        tenant.park("event-1");
        tenant.changeBeforeActing = true;

        WebEndpointResponse<OutboxEndpoint.Outcome> replay = endpoint.replay("event-1", "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(OutboxEndpoint.STATUS_CONFLICT, replay.getStatus());
        assertEquals(OutboxEndpoint.CHANGED, replay.getBody().errorCode());
        assertEquals(List.of("audited MESSAGE_REPLAYED", "audited MESSAGE_REPLAYED failed " + OutboxEndpoint.CHANGED),
            happened);
        assertTrue(audit.recorded().get(0).errorCode() == null);
    }

    @Test
    void withoutOutboxesThereIsNothingToListOrChange() {
        OutboxEndpoint none = new OutboxEndpoint(() -> null, () -> audit, RETENTION, Clock.systemUTC());

        assertEquals(Map.of(), none.parked().outboxes());
        assertEquals(WebEndpointResponse.STATUS_NOT_FOUND, none.replay("event-1", "ops-alice", "why",
            SecurityContext.NONE).getStatus());
    }

    private static ParkedOutboxMessage parked(String eventId) {
        OffsetDateTime occurred = OffsetDateTime.parse("2026-10-28T08:00:00Z");
        return new ParkedOutboxMessage(eventId, eventId, "TenantFirstOwnerAccepted", "1.0.0", "platform-tenant", 910001L,
            8, "EVENT_HANDLER_FAILED", occurred, occurred.plusMinutes(5));
    }

    /** An outbox that keeps each message's status, and tells the test what it did. */
    private final class Store implements OutboxMessageStore {

        private final Map<String, OutboxMessageState> messages = new LinkedHashMap<>();
        private int limit;
        private boolean changeBeforeActing;

        void park(String eventId) {
            messages.put(eventId, new OutboxMessageState("PARKED", null));
        }

        void publish(String eventId, OffsetDateTime publishedAt) {
            messages.put(eventId, new OutboxMessageState("PUBLISHED", publishedAt));
        }

        String status(String eventId) {
            return messages.get(eventId).status();
        }

        @Override
        public List<ParkedOutboxMessage> listParked(int limit) {
            this.limit = limit;
            return messages.entrySet().stream()
                .filter(message -> "PARKED".equals(message.getValue().status()))
                .map(message -> parked(message.getKey()))
                .toList();
        }

        @Override
        public Optional<OutboxMessageState> find(String eventId) {
            return Optional.ofNullable(messages.get(eventId));
        }

        @Override
        public boolean replayParked(String eventId, OffsetDateTime now) {
            return move(eventId, "PARKED", "PENDING", "replayed " + eventId + "@" + now);
        }

        @Override
        public boolean discardParked(String eventId, OffsetDateTime now) {
            return move(eventId, "PARKED", "DISCARDED", "discarded " + eventId + "@" + now);
        }

        @Override
        public boolean republish(String eventId, OffsetDateTime now, OffsetDateTime publishedSince) {
            OutboxMessageState stored = messages.get(eventId);
            if (stored == null || stored.publishedAt() == null || stored.publishedAt().isBefore(publishedSince)) {
                return false;
            }
            return move(eventId, "PUBLISHED", "PENDING", "republished " + eventId + "@" + now + " since "
                + publishedSince);
        }

        private boolean move(String eventId, String from, String to, String what) {
            if (changeBeforeActing) {
                messages.put(eventId, new OutboxMessageState("PUBLISHED", AT));
            }
            OutboxMessageState stored = messages.get(eventId);
            if (stored == null || !from.equals(stored.status())) {
                return false;
            }
            messages.put(eventId, new OutboxMessageState(to, null));
            happened.add(what);
            return true;
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
        public void releaseUntried(String relayOwner, String messageId) {
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
