/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

import io.runtime.orchestrator.event.conformance.ConformanceWorld;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Parked events are listed without payloads and replayed by event id, through the delivery core (Kafka design
 * §4.2, D20; root BRX-RS-MESSAGE-028).
 */
class EventsEndpointTest {

    private static final ConsumedSubscription SUBSCRIPTION =
        new ConsumedSubscription(ConformanceWorld.SUBSCRIBER, ConformanceWorld.HANDLER, ConformanceWorld.EVENT);

    private final ConformanceWorld world = new ConformanceWorld();
    private final ParkingBroker broker = new ParkingBroker();
    private final EventsEndpoint endpoint = new EventsEndpoint(() -> broker, world::newDelivery);

    @Test
    void theParkedEventsAreListedBySubscription() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        Map<String, List<ParkedEvent>> listed = endpoint.parked().subscriptions();

        assertEquals(Set.of(SUBSCRIPTION.name()), listed.keySet());
        assertEquals("event-1", listed.get(SUBSCRIPTION.name()).get(0).eventId());
    }

    @Test
    void aReplayDeliversTheEventWithItsIdAndReleasesIt() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        WebEndpointResponse<EventsEndpoint.Replay> replay = endpoint.replay("event-1");

        assertEquals(200, replay.getStatus());
        assertEquals(List.of("event-1"), world.handledIds());
        assertTrue(broker.parked.isEmpty());
    }

    @Test
    void anEventNoSubscriptionParkedIsNotFound() {
        assertEquals(404, endpoint.replay("event-1").getStatus());
    }

    @Test
    void aReplayTheSubscriberStillRefusesKeepsTheEventParked() {
        world.failing(true);
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        WebEndpointResponse<EventsEndpoint.Replay> replay = endpoint.replay("event-1");

        assertEquals(409, replay.getStatus());
        assertFalse(replay.getBody().subscriptions().get(0).replayed());
        assertEquals(EventDelivery.HANDLER_FAILED, replay.getBody().subscriptions().get(0).errorCode());
        assertTrue(broker.parked.containsKey("event-1"));
    }

    @Test
    void aRecordThatIsNoEnvelopeCannotBeReplayed() {
        broker.parked.put("kafka-0@7", null);

        WebEndpointResponse<EventsEndpoint.Replay> replay = endpoint.replay("kafka-0@7");

        assertEquals(409, replay.getStatus());
        assertEquals(SubscriptionDelivery.ENVELOPE_INVALID, replay.getBody().subscriptions().get(0).errorCode());
    }

    /** A broker whose one subscription holds what the test parks; {@code null} stands for a record that is no envelope. */
    private static final class ParkingBroker implements EventSubscriptionTransport {

        private final Map<String, CanonicalOutboxMessage> parked = new HashMap<>();

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
            return Map.of(SUBSCRIPTION.name(), parked.keySet().stream()
                .map(eventId -> new ParkedEvent(SUBSCRIPTION.name(), eventId, ConformanceWorld.EVENT,
                    EventDelivery.HANDLER_FAILED, 3, OffsetDateTime.now(), "test"))
                .toList());
        }

        @Override
        public Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId) {
            return parked.containsKey(eventId) && subscription.equals(SUBSCRIPTION)
                ? Optional.of(new ParkedEvent(SUBSCRIPTION.name(), eventId, null, "code", 1, OffsetDateTime.now(), "test"))
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
