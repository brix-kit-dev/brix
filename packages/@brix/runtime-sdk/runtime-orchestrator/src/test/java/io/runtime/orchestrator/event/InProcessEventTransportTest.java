/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * Events reach each subscriber in its owner's unit of work, once per inbox, with the envelope's tenant
 * (P4 design §7.3).
 */
class InProcessEventTransportTest {

    private static final String EVENT = "TenantFirstOwnerAccepted";
    private static final String PUBLISHER = "platform-tenant";
    private static final String SUBSCRIBER = "platform-tenant";
    private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");

    private final RecordingPersistence persistence = new RecordingPersistence();
    private final Set<String> inbox = new HashSet<>();
    private final List<EventInvocation<Accepted>> handled = new ArrayList<>();
    private final List<TenantScope> scopes = new ArrayList<>();
    private RuntimeException failure;
    private boolean started = true;
    private String publisher = PUBLISHER;

    /** Payload the test handler declares. */
    public record Accepted(Long tenantId, Long memberId) {
    }

    private final class AcceptedHandler implements EventHandler<Accepted> {
        @Override
        public CompletionStage<Void> handle(EventInvocation<Accepted> invocation) {
            scopes.add(TenantScopes.requested());
            if (failure != null) {
                throw failure;
            }
            handled.add(invocation);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void aTenantEventIsHandledInTheSubscribersUnitOfWorkWithTheEnvelopesTenant() {
        transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1));

        assertEquals(1, handled.size());
        EventInvocation<Accepted> invocation = handled.get(0);
        assertEquals(new Accepted(910001L, 7L), invocation.event().payload());
        assertEquals("event-1", invocation.event().eventId());
        assertEquals("910001", invocation.event().tenantId());
        assertEquals(PUBLISHER, invocation.publisher().producerPluginId());
        assertEquals(NOW.plusSeconds(15), invocation.deadline());
        assertTrue(invocation.trace().traceparent().startsWith("00-"), "a trace is made when the row has none");
        assertEquals(List.of(TenantScope.tenant(910001L)), scopes);
        assertEquals(List.of("required"), persistence.modes());
        assertEquals(Set.of(SUBSCRIBER + "|projection.v1|event-1"), inbox);
    }

    @Test
    void anEventTheInboxAlreadyHoldsIsNotHandledAgain() {
        inbox.add(SUBSCRIBER + "|projection.v1|event-1");

        transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 2));

        assertTrue(handled.isEmpty());
    }

    @Test
    void anEventFromAnotherPublisherIsParked() {
        publisher = "someone-else";

        OutboxTransportException parked = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{}", "1.0.0", 1)));

        assertEquals(InProcessEventTransport.PUBLISHER_REJECTED, parked.errorCode());
        assertTrue(parked.permanent());
        assertTrue(handled.isEmpty());
    }

    @Test
    void aSchemaVersionTheSubscriptionDoesNotAcceptIsParked() {
        OutboxTransportException parked = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "2.0.0", 1)));

        assertEquals(InProcessEventTransport.SCHEMA_UNSUPPORTED, parked.errorCode());
        assertTrue(parked.permanent());
    }

    @Test
    void aPayloadThatDoesNotDecodeIsParked() {
        OutboxTransportException parked = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":\"not a number\"}", "1.0.0", 1)));

        assertEquals(InProcessEventTransport.PAYLOAD_INVALID, parked.errorCode());
        assertTrue(parked.permanent());
        assertTrue(inbox.isEmpty());
    }

    @Test
    void aSubscriberThatHasNotStartedMakesTheEventWait() {
        started = false;

        OutboxTransportException waiting = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1)));

        assertEquals(InProcessEventTransport.SUBSCRIBER_NOT_READY, waiting.errorCode());
        assertFalse(waiting.permanent());
    }

    @Test
    void aFailingHandlerMakesTheEventWaitForARetry() {
        failure = new IllegalStateException("projection unavailable");

        OutboxTransportException retry = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1)));

        assertEquals(InProcessEventTransport.HANDLER_FAILED, retry.errorCode());
        assertFalse(retry.permanent());
        assertEquals(failure, retry.getCause());
    }

    @Test
    void onlyEventsAreDeliveredInProcess() {
        CanonicalOutboxMessage command = new CanonicalOutboxMessage("command-1", "command-1", "COMMAND", "DoSomething",
            "1.0.0", "CRITICAL", PUBLISHER, "PLATFORM", null, "k", OffsetDateTime.now(), "correlation-1", null, null, null, "{}", 1);

        assertTrue(assertThrows(OutboxTransportException.class, () -> transport().publish(command)).permanent());
    }

    private InProcessEventTransport transport() {
        PluginRuntimeDescriptor.EventSubscription subscription = new PluginRuntimeDescriptor.EventSubscription(
            "projection", EVENT, ">=1.0.0 <2.0.0", "projection.v1", "event-standard", "persistent-inbox", "projection-scope");
        BoundEventHandler bound = new BoundEventHandler(SUBSCRIBER, subscription, new AcceptedHandler(), Accepted.class);
        EventSubscribers subscribers = new EventSubscribers() {
            @Override
            public Optional<String> publisherOf(String eventType) {
                return EVENT.equals(eventType) ? Optional.of(publisher) : Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return List.of(new DeclaredSubscriber(SUBSCRIBER, subscription, started ? bound : null));
            }
        };
        EventInboxStore store = (owner, handlerId, message) -> inbox.add(owner + "|" + handlerId + "|" + message.eventId());
        return new InProcessEventTransport(subscribers,
            owner -> SUBSCRIBER.equals(owner) ? Optional.of(persistence) : Optional.empty(), store, new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(15));
    }

    private static CanonicalOutboxMessage message(String payload, String schemaVersion, int attempt) {
        return new CanonicalOutboxMessage("event-1", "event-1", "EVENT", EVENT, schemaVersion, "CRITICAL", PUBLISHER,
            "TENANT", 910001L, "910001", OffsetDateTime.parse("2026-10-28T08:59:00Z"), "correlation-1", null, null, null,
            payload, attempt);
    }
}
