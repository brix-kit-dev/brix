/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

import io.runtime.orchestrator.event.conformance.ConformanceWorld;
import io.runtime.orchestrator.event.conformance.RecordingMessageAudit;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.MessageOperationAudit;
import io.runtime.orchestrator.outbox.MessageOperationAudit.MessageOperation;

class EventsEndpointTest {

    private static final ConsumedSubscription SUBSCRIPTION =
        new ConsumedSubscription(ConformanceWorld.SUBSCRIBER, ConformanceWorld.HANDLER, ConformanceWorld.EVENT);
    private static final String NAME = SUBSCRIPTION.name();

    private final ConformanceWorld world = new ConformanceWorld();
    private final ParkingBroker broker = new ParkingBroker();
    private final RecordingMessageAudit audit = new RecordingMessageAudit(
        operation -> assertTrue(broker.parked.containsKey(operation.messageId()) || operation.errorCode() != null,
            "the audit record comes before the action"));
    private final EventsEndpoint endpoint = new EventsEndpoint(() -> broker, world::newDelivery, () -> audit);

    @Test
    void theParkedEventsAreListedBySubscription() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        Map<String, List<ParkedEvent>> listed = endpoint.parked().subscriptions();

        assertEquals(Set.of(NAME), listed.keySet());
        assertEquals("event-1", listed.get(NAME).get(0).eventId());
    }

    @Test
    void aReplayIsAuditedThenDeliversTheEventWithItsIdAndReleasesIt() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        WebEndpointResponse<EventsEndpoint.Outcome> replay = endpoint.replay(NAME, "event-1", "ops-alice",
            "projection fixed", SecurityContext.NONE);

        assertEquals(200, replay.getStatus());
        assertEquals(new EventsEndpoint.Outcome(NAME, "event-1", true, null), replay.getBody());
        assertEquals(List.of("event-1"), world.handledIds());
        assertTrue(broker.parked.isEmpty());
        assertEquals(List.of(MessageOperation.of(MessageOperationAudit.REPLAYED, "event-1", NAME, "ops-alice", null,
            "projection fixed")), audit.recorded());
    }

    @Test
    void anEventTheSubscriptionHasNotParkedIsNotFound() {
        assertEquals(404, endpoint.replay(NAME, "event-1", "ops-alice", "why", SecurityContext.NONE).getStatus());
        assertEquals(404, endpoint.replay("other.subscription", "event-1", "ops-alice", "why", SecurityContext.NONE)
            .getStatus());
        assertEquals(List.of(), audit.recorded());
    }

    @Test
    void aReplayTheSubscriberStillRefusesKeepsTheEventParkedAndIsRecordedAsFailed() {
        world.failing(true);
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        WebEndpointResponse<EventsEndpoint.Outcome> replay = endpoint.replay(NAME, "event-1", "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(409, replay.getStatus());
        assertFalse(replay.getBody().done());
        assertEquals(EventDelivery.HANDLER_FAILED, replay.getBody().errorCode());
        assertTrue(broker.parked.containsKey("event-1"));
        assertEquals(2, audit.recorded().size());
        assertNull(audit.recorded().get(0).errorCode());
        assertEquals(EventDelivery.HANDLER_FAILED, audit.recorded().get(1).errorCode());
    }

    @Test
    void aRecordThatIsNoEnvelopeCannotBeReplayedButCanBeDiscarded() {
        broker.parked.put("kafka-0@7", null);

        WebEndpointResponse<EventsEndpoint.Outcome> replay = endpoint.replay(NAME, "kafka-0@7", "ops-alice", "why",
            SecurityContext.NONE);
        WebEndpointResponse<EventsEndpoint.Outcome> discard = endpoint.discard(NAME, "kafka-0@7", "ops-alice",
            "not an envelope", SecurityContext.NONE);

        assertEquals(409, replay.getStatus());
        assertEquals(SubscriptionDelivery.ENVELOPE_INVALID, replay.getBody().errorCode());
        assertEquals(200, discard.getStatus());
        assertTrue(broker.parked.isEmpty());
        assertEquals(MessageOperationAudit.DISCARDED, audit.recorded().get(2).action());
        assertEquals("not an envelope", audit.recorded().get(2).reason());
    }

    @Test
    void aDiscardReleasesTheEventWithoutDeliveringIt() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        WebEndpointResponse<EventsEndpoint.Outcome> discard = endpoint.discard(NAME, "event-1", "ops-alice",
            "superseded by event-2", SecurityContext.NONE);

        assertEquals(200, discard.getStatus());
        assertTrue(broker.parked.isEmpty());
        assertEquals(List.of(), world.handledIds());
        assertEquals(List.of(MessageOperation.of(MessageOperationAudit.DISCARDED, "event-1", NAME, "ops-alice", null,
            "superseded by event-2")), audit.recorded());
    }

    @Test
    void aSubscriptionsParkedEventsAreReplayedTheEarliestFirst() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));
        broker.park(world.publish("event-2", "member-2", ConformanceWorld.TENANT, 2));
        broker.park(world.publish("event-3", "member-1", ConformanceWorld.TENANT, 1));

        WebEndpointResponse<EventsEndpoint.Batch> batch = endpoint.replayAll(NAME, "ops-alice", "projection fixed",
            SecurityContext.NONE);

        assertEquals(200, batch.getStatus());
        assertEquals(List.of("event-1", "event-2", "event-3"), world.handledIds());
        assertEquals(3, batch.getBody().events().size());
        assertTrue(broker.parked.isEmpty());
        assertEquals(List.of("event-1", "event-2", "event-3"),
            audit.recorded().stream().map(MessageOperation::messageId).toList());
    }

    @Test
    void aBatchReplayStopsAtTheFirstEventThatFailsAgain() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));
        broker.park(world.publish("event-2", "member-2", ConformanceWorld.TENANT, 2));
        broker.park(world.publish("event-3", "member-1", ConformanceWorld.TENANT, 1));
        world.failNextOf("event-2", 1);

        WebEndpointResponse<EventsEndpoint.Batch> batch = endpoint.replayAll(NAME, "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(409, batch.getStatus());
        assertEquals(List.of("event-1"), world.handledIds());
        assertEquals(List.of(true, false), batch.getBody().events().stream().map(EventsEndpoint.Outcome::done).toList());
        assertEquals(Set.of("event-2", "event-3"), broker.parked.keySet());
    }

    @Test
    void anActionThePlatformAuditCannotRecordIsRefused() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));
        audit.refusing(true);

        assertEquals(EventsEndpoint.STATUS_UNAVAILABLE, endpoint.replay(NAME, "event-1", "ops-alice", "why",
            SecurityContext.NONE).getStatus());
        assertEquals(EventsEndpoint.STATUS_UNAVAILABLE, endpoint.discard(NAME, "event-1", "ops-alice", "why",
            SecurityContext.NONE).getStatus());
        assertEquals(EventsEndpoint.STATUS_UNAVAILABLE, endpoint.replayAll(NAME, "ops-alice", "why",
            SecurityContext.NONE).getStatus());
        assertEquals(List.of(), world.handledIds());
        assertTrue(broker.parked.containsKey("event-1"));
    }

    @Test
    void withoutAPlatformAuditEveryActionIsRefused() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));
        EventsEndpoint unaudited = new EventsEndpoint(() -> broker, world::newDelivery, () -> null);

        assertEquals(EventsEndpoint.STATUS_UNAVAILABLE, unaudited.discard(NAME, "event-1", "ops-alice", "why",
            SecurityContext.NONE).getStatus());
        assertTrue(broker.parked.containsKey("event-1"));
    }

    @Test
    void anActionNeedsAnOperatorAndAReason() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        assertEquals(EventsEndpoint.STATUS_BAD_REQUEST, endpoint.replay(NAME, "event-1", "", "why",
            SecurityContext.NONE).getStatus());
        assertEquals(EventsEndpoint.STATUS_BAD_REQUEST, endpoint.discard(NAME, "event-1", "ops-alice", " ",
            SecurityContext.NONE).getStatus());
        assertEquals(EventsEndpoint.STATUS_BAD_REQUEST, endpoint.replayAll(NAME, "ops-alice", null,
            SecurityContext.NONE).getStatus());
        assertEquals(List.of(), audit.recorded());
    }

    /** A broker whose one subscription holds what the test parks, in order; {@code null} is a record that is no envelope. */
    private static final class ParkingBroker implements EventSubscriptionTransport {

        private final Map<String, CanonicalOutboxMessage> parked = new LinkedHashMap<>();

        void park(CanonicalOutboxMessage message) {
            parked.put(message.eventId(), message);
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public void prepare(Set<String> eventTypes, List<ConsumedSubscription> subscriptions) {
        }

        @Override
        public Consumption consume(ConsumedSubscription subscription, SubscriptionDelivery delivery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, List<ParkedEvent>> parked(int limit) {
            return Map.of(NAME, parked.keySet().stream()
                .limit(limit)
                .map(eventId -> new ParkedEvent(NAME, eventId, ConformanceWorld.EVENT,
                    EventDelivery.HANDLER_FAILED, 3, OffsetDateTime.now(), "test"))
                .toList());
        }

        @Override
        public Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId) {
            return parked.containsKey(eventId) && subscription.equals(SUBSCRIPTION)
                ? Optional.of(new ParkedEvent(NAME, eventId, null, "code", 1, OffsetDateTime.now(), "test"))
                : Optional.empty();
        }

        @Override
        public Optional<CanonicalOutboxMessage> parkedMessage(ConsumedSubscription subscription, String eventId) {
            return Optional.ofNullable(parked.get(eventId));
        }

        @Override
        public void release(ConsumedSubscription subscription, String eventId) {
            parked.remove(eventId);
        }
    }
}
