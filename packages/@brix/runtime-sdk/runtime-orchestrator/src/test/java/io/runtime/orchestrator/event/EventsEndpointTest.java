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
import io.runtime.sdk.command.CommandStatus;

class EventsEndpointTest {

    private static final ConsumedSubscription SUBSCRIPTION =
        new ConsumedSubscription(ConformanceWorld.SUBSCRIBER, ConformanceWorld.HANDLER, ConformanceWorld.EVENT);
    private static final String NAME = SUBSCRIPTION.name();
    private static final ConsumedSubscription COMMANDS = new ConsumedSubscription(ConformanceWorld.SUBSCRIBER,
        ConformanceWorld.COMMAND_HANDLER, ConformanceWorld.COMMAND, ConsumedSubscription.COMMAND);

    private final ConformanceWorld world = new ConformanceWorld();
    private final ParkingBroker broker = new ParkingBroker();
    private final RecordingMessageAudit audit = new RecordingMessageAudit(
        operation -> assertTrue(broker.parked.containsKey(operation.messageId()) || operation.errorCode() != null,
            "the audit record comes before the action"));
    private final EventsEndpoint endpoint = new EventsEndpoint(() -> broker, world::newDelivery, () -> audit);

    @Test
    void theParkedEventsAreListedBySubscription() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));

        Map<String, List<ParkedEvent>> listed = endpoint.parked().getBody().subscriptions();

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
    void aRecordTheBrokerDoesNotGiveBackLeavesTheEventParkedAndIsRecordedAsFailed() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));
        broker.unreadable = true;

        WebEndpointResponse<EventsEndpoint.Outcome> replay = endpoint.replay(NAME, "event-1", "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(503, replay.getStatus());
        assertFalse(replay.getBody().done());
        assertEquals(EventsEndpoint.PARKED_UNREADABLE, replay.getBody().errorCode());
        assertTrue(broker.parked.containsKey("event-1"));
        assertEquals(List.of(), world.handledIds());
        assertEquals(EventsEndpoint.PARKED_UNREADABLE, audit.recorded().get(1).errorCode());

        broker.unreadable = false;
        assertEquals(200, endpoint.replay(NAME, "event-1", "ops-alice", "again", SecurityContext.NONE).getStatus());
        assertEquals(List.of("event-1"), world.handledIds());
    }

    @Test
    void aBatchReplayStopsAtARecordTheBrokerDoesNotGiveBack() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));
        broker.park(world.publish("event-2", "member-2", ConformanceWorld.TENANT, 2));
        broker.unreadable = true;

        WebEndpointResponse<EventsEndpoint.Batch> batch = endpoint.replayAll(NAME, "ops-alice", "why",
            SecurityContext.NONE);

        assertEquals(503, batch.getStatus());
        assertEquals(1, batch.getBody().events().size());
        assertEquals(Set.of("event-1", "event-2"), broker.parked.keySet());
    }

    @Test
    void aCommandDiscardWhoseRecordTheBrokerDoesNotGiveBackLeavesItParked() {
        broker.park(world.submit("command-1", "order-7", ConformanceWorld.TENANT, null));
        broker.unreadable = true;

        WebEndpointResponse<EventsEndpoint.Outcome> discard = endpoint.discard(COMMANDS.name(), "command-1",
            "ops-alice", "the order was cancelled by hand", SecurityContext.NONE);

        assertEquals(503, discard.getStatus());
        assertEquals(EventsEndpoint.PARKED_UNREADABLE, discard.getBody().errorCode());
        assertTrue(broker.parked.containsKey("command-1"), "its sender would never learn the command was discarded");
    }

    @Test
    void whileTheBrokerCannotTellWhatIsParkedNothingIsAnsweredAsIfNothingWere() {
        broker.park(world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1));
        broker.notKnown = true;

        assertEquals(503, endpoint.parked().getStatus());
        assertNull(endpoint.parked().getBody(), "an empty list would read as nothing parked");
        WebEndpointResponse<EventsEndpoint.Outcome> replay = endpoint.replay(NAME, "event-1", "ops-alice", "why",
            SecurityContext.NONE);
        assertEquals(503, replay.getStatus());
        assertEquals(EventsEndpoint.PARKED_UNREADABLE, replay.getBody().errorCode());
        assertEquals(503, endpoint.discard(NAME, "event-1", "ops-alice", "why", SecurityContext.NONE).getStatus());
        assertEquals(503, endpoint.replayAll(NAME, "ops-alice", "why", SecurityContext.NONE).getStatus());

        assertTrue(broker.parked.containsKey("event-1"));
        assertEquals(List.of(), audit.recorded(), "nothing happened, so there is nothing to record");
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
    void aDiscardedCommandIsRecordedForItsSenderBeforeItIsReleased() {
        // Standard-link plan point 7: on a broker transport the provider parks commands, so the discard says so.
        broker.park(world.submit("command-1", "order-7", ConformanceWorld.TENANT, null));

        WebEndpointResponse<EventsEndpoint.Outcome> discard = endpoint.discard(COMMANDS.name(), "command-1",
            "ops-alice", "the order was cancelled by hand", SecurityContext.NONE);

        assertEquals(200, discard.getStatus());
        assertTrue(broker.parked.isEmpty());
        CommandStatus status = world.commandStatus(ConformanceWorld.PUBLISHER, ConformanceWorld.TENANT, "command-1",
            broker);
        assertEquals(CommandStatus.State.DISCARDED, status.state());
        assertEquals("code", status.errorCode(), "why the provider had parked it");
        assertEquals(List.of(), world.handledCommands());
    }

    @Test
    void aCommandDiscardTheProviderCannotRecordLeavesItParked() {
        broker.park(world.submit("command-1", "order-7", ConformanceWorld.TENANT, null));
        world.subscriberDatabaseDown(true);

        WebEndpointResponse<EventsEndpoint.Outcome> discard = endpoint.discard(COMMANDS.name(), "command-1",
            "ops-alice", "the order was cancelled by hand", SecurityContext.NONE);

        assertEquals(409, discard.getStatus());
        assertEquals(EventsEndpoint.DISCARD_UNRECORDED, discard.getBody().errorCode());
        assertTrue(broker.parked.containsKey("command-1"));
        assertEquals(List.of(MessageOperationAudit.DISCARDED, MessageOperationAudit.DISCARDED),
            audit.recorded().stream().map(MessageOperation::action).toList());
        assertEquals(EventsEndpoint.DISCARD_UNRECORDED, audit.recorded().get(1).errorCode());
        world.subscriberDatabaseDown(false);
        assertEquals(CommandStatus.State.PARKED, world.commandStatus(ConformanceWorld.PUBLISHER,
            ConformanceWorld.TENANT, "command-1", broker).state());
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
        private boolean unreadable;
        private boolean notKnown;

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
            if (notKnown) {
                throw new IllegalStateException("this host has not read what is parked yet");
            }
            return Map.of(NAME, parked.keySet().stream()
                .limit(limit)
                .map(eventId -> new ParkedEvent(NAME, eventId, ConformanceWorld.EVENT,
                    EventDelivery.HANDLER_FAILED, 3, OffsetDateTime.now(), "test", null, null))
                .toList());
        }

        @Override
        public Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId) {
            if (notKnown) {
                throw new IllegalStateException("this host has not read what is parked yet");
            }
            if (!parked.containsKey(eventId) || !subscription.equals(subscriptionOf(eventId))) {
                return Optional.empty();
            }
            CanonicalOutboxMessage message = parked.get(eventId);
            return Optional.of(new ParkedEvent(subscription.name(), eventId, null, "code", 1, OffsetDateTime.now(), "test",
                message == null ? null : message.producerPluginId(), message == null ? null : message.tenantId()));
        }

        /** Commands park on the provider's command subscription, events and other records on the one subscription. */
        private static ConsumedSubscription subscriptionOf(CanonicalOutboxMessage message) {
            return message != null && "COMMAND".equals(message.messageKind()) ? COMMANDS : SUBSCRIPTION;
        }

        private ConsumedSubscription subscriptionOf(String eventId) {
            return subscriptionOf(parked.get(eventId));
        }

        @Override
        public Optional<CanonicalOutboxMessage> parkedMessage(ConsumedSubscription subscription, String eventId) {
            if (unreadable) {
                throw new IllegalStateException("the broker did not give the record back");
            }
            return Optional.ofNullable(parked.get(eventId));
        }

        @Override
        public void release(ConsumedSubscription subscription, String eventId) {
            parked.remove(eventId);
        }
    }
}
