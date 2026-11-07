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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.conformance.RecordingMessageTracing;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.event.EventReliability;
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
    private boolean pluginsStarted = true;
    private String publisher = PUBLISHER;
    private String idempotencyPolicy = PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX;
    private boolean ownsPersistence = true;
    private MessageTracing tracing = MessageTracing.NOOP;
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private Runnable whileHandling = () -> { };
    private final List<Duration> timeouts = new ArrayList<>();

    /** Payload the test handler declares. */
    public record Accepted(Long tenantId, Long memberId, Role role) {

        /** The values the subscriber knows. */
        public enum Role {
            OWNER
        }

        Accepted(Long tenantId, Long memberId) {
            this(tenantId, memberId, null);
        }
    }

    private final class AcceptedHandler implements EventHandler<Accepted> {
        @Override
        public CompletionStage<Void> handle(EventInvocation<Accepted> invocation) {
            scopes.add(TenantScopes.requested());
            if (failure != null) {
                throw failure;
            }
            whileHandling.run();
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
    void untilThePluginsStartAnEventWaitsInsteadOfBeingJudged() {
        pluginsStarted = false;
        publisher = null;

        InProcessEventTransport transport = transport();
        OutboxTransportException waiting = assertThrows(OutboxTransportException.class,
            () -> transport.publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1)));

        assertFalse(transport.ready());
        assertEquals(InProcessEventTransport.SUBSCRIBER_NOT_READY, waiting.errorCode());
        assertFalse(waiting.permanent());
        assertTrue(waiting.unavailable(), "the relay waits without spending an attempt (Kafka design §14 K11)");
        assertTrue(handled.isEmpty());
        assertTrue(inbox.isEmpty());
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
    void anEnumValueThePublisherAddedInAMinorVersionReachesTheSubscriberAsEmpty() {
        // Root BRX-RS-CAP-021; standard-link plan L5.3: the subscriber's own type knows fewer values.
        transport().publish(message("{\"tenantId\":910001,\"memberId\":7,\"role\":\"AUDITOR\"}", "1.1.0", 1));

        assertEquals(new Accepted(910001L, 7L, null), handled.get(0).event().payload());
    }

    @Test
    void aSubscriberThatHasNotStartedMakesTheEventWait() {
        started = false;

        OutboxTransportException waiting = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1)));

        assertEquals(InProcessEventTransport.SUBSCRIBER_NOT_READY, waiting.errorCode());
        assertFalse(waiting.permanent());
        assertTrue(waiting.unavailable(), "the relay waits without spending an attempt (Kafka design §14 K11)");
    }

    @Test
    void aSubscriberWhoseDatabaseCannotBeReachedMakesTheRelayWaitForIt() {
        failure = new PersistenceCapabilityException(PersistenceCapabilityException.Code.UNAVAILABLE,
            java.util.Map.of("owner", SUBSCRIBER));

        OutboxTransportException waiting = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1)));

        assertEquals(SubscriptionDelivery.OWNER_UNAVAILABLE, waiting.errorCode());
        assertFalse(waiting.permanent());
        assertTrue(waiting.unavailable(), "the relay waits without spending an attempt (Kafka design §14 K11)");
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
    void theSubscribersUnitOfWorkLastsNoLongerThanTheTimeLeftBeforeItsDeadline() {
        // Standard-link plan point 3 (root BRX-RS-DATA-011): its statements stop, and its waits for a connection end,
        // at the delivery's deadline.
        transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1));

        assertEquals(List.of(Duration.ofSeconds(15)), timeouts);
        assertEquals(1, handled.size());
    }

    @Test
    void aHandlerThatRunsPastItsDeadlineFailsBeforeItsUnitOfWorkCommits() {
        // Standard-link plan point 3: a synchronous handler did its work before it returned, past its deadline. The
        // delivery fails inside the unit of work, so the inbox record and the handler's writes roll back.
        MutableClock moving = new MutableClock(NOW);
        clock = moving;
        whileHandling = () -> moving.advance(Duration.ofSeconds(16));

        OutboxTransportException retry = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1)));

        assertEquals(InProcessEventTransport.HANDLER_FAILED, retry.errorCode());
        assertFalse(retry.permanent());
        assertTrue(retry.getCause().getMessage().contains("finished after its deadline"),
            retry.getCause().getMessage());
        assertTrue(handled.get(0).cancellation().isCancelled(), "the handler is told the runtime stopped waiting");
        assertEquals(List.of("required"), persistence.modes(), "it failed inside its unit of work");
    }

    @Test
    void aSubscriptionWithoutAnInboxHandlesEachDeliveryInItsOwnersUnitOfWork() {
        idempotencyPolicy = PluginRuntimeDescriptor.EventSubscription.NO_DURABLE_IDEMPOTENCY;
        InProcessEventTransport transport = transport();

        transport.publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1, "BEST_EFFORT"));
        transport.publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1, "BEST_EFFORT"));

        assertEquals(2, handled.size(), "nothing skips the second delivery");
        assertEquals(EventReliability.BEST_EFFORT, handled.get(0).event().reliability());
        assertTrue(inbox.isEmpty());
        assertEquals(List.of("required", "required"), persistence.modes());
        assertEquals(List.of(TenantScope.tenant(910001L), TenantScope.tenant(910001L)), scopes);
    }

    @Test
    void aSubscriberWithoutPersistenceAndWithoutAnInboxHandlesTheEventInItsTenant() {
        idempotencyPolicy = PluginRuntimeDescriptor.EventSubscription.NO_DURABLE_IDEMPOTENCY;
        ownsPersistence = false;

        transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1, "BEST_EFFORT"));

        assertEquals(1, handled.size());
        assertTrue(persistence.modes().isEmpty());
        assertEquals(List.of(TenantScope.tenant(910001L)), scopes);
    }

    @Test
    void aCriticalEventIsParkedAtASubscriptionWithoutAnInbox() {
        idempotencyPolicy = PluginRuntimeDescriptor.EventSubscription.NO_DURABLE_IDEMPOTENCY;

        OutboxTransportException parked = assertThrows(OutboxTransportException.class,
            () -> transport().publish(message("{\"tenantId\":910001,\"memberId\":7}", "1.0.0", 1)));

        assertEquals(InProcessEventTransport.RELIABILITY_UNSUPPORTED, parked.errorCode());
        assertTrue(parked.permanent());
        assertTrue(handled.isEmpty());
    }

    @Test
    void aDeliveryIsHandledInItsConsumerSpanAndAFailedOneMarksIt() {
        // Root BRX-RS-OTEL-002, OTEL-005: the handler runs in a CONSUMER span whose parent is the message's context.
        RecordingMessageTracing recording = new RecordingMessageTracing();
        tracing = recording;
        CanonicalOutboxMessage traced = traced("event-1");

        transport().publish(traced);
        failure = new IllegalStateException("projection unavailable");
        assertThrows(OutboxTransportException.class, () -> transport().publish(traced("event-2")));

        List<RecordingMessageTracing.Recorded> spans = recording.spans(RecordingMessageTracing.CONSUMER);
        assertEquals(2, spans.size());
        assertEquals(traced.traceparent(), spans.get(0).parent());
        assertEquals(SUBSCRIBER + ".projection.v1", spans.get(0).subscription());
        assertEquals(spans.get(0).traceparent(), handled.get(0).trace().traceparent());
        assertEquals("vendor=1", handled.get(0).trace().tracestate());
        assertFalse(spans.get(0).failed());
        assertTrue(spans.get(1).failed());
    }

    @Test
    void onlyEventsAreDeliveredInProcess() {
        CanonicalOutboxMessage command = new CanonicalOutboxMessage("command-1", "command-1", "COMMAND", "DoSomething",
            "1.0.0", "CRITICAL", PUBLISHER, "PLATFORM", null, "k", OffsetDateTime.now(), "correlation-1", null, null, null, "{}", 1);

        assertTrue(assertThrows(OutboxTransportException.class, () -> transport().publish(command)).permanent());
    }

    private InProcessEventTransport transport() {
        PluginRuntimeDescriptor.EventSubscription subscription = new PluginRuntimeDescriptor.EventSubscription(
            "projection", EVENT, ">=1.0.0 <2.0.0", "projection.v1", "event-standard", idempotencyPolicy, "projection-scope");
        BoundEventHandler bound = new BoundEventHandler(SUBSCRIBER, subscription, new AcceptedHandler(), Accepted.class);
        EventSubscribers subscribers = new EventSubscribers() {
            @Override
            public boolean ready() {
                return pluginsStarted;
            }

            @Override
            public Optional<String> publisherOf(String eventType) {
                return EVENT.equals(eventType) ? Optional.ofNullable(publisher) : Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return List.of(new DeclaredSubscriber(SUBSCRIBER, subscription, started ? bound : null));
            }

            @Override
            public List<DeclaredSubscriber> subscriptions() {
                return subscribersOf(EVENT);
            }

            @Override
            public Set<String> publishedEventTypes() {
                return Set.of(EVENT);
            }
        };
        EventInboxStore store = (owner, handlerId, message) -> inbox.add(owner + "|" + handlerId + "|" + message.eventId());
        OwnerPersistenceProvider owners = new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
                return SUBSCRIBER.equals(ownerModuleId) && ownsPersistence
                    ? Optional.of(persistence)
                    : Optional.empty();
            }

            @Override
            public <T> T readWrite(String ownerModuleId, Duration timeout, Supplier<T> work) {
                timeouts.add(timeout);
                return forOwner(ownerModuleId).map(owner -> owner.unitOfWork().required(work)).orElseGet(work);
            }
        };
        return new InProcessEventTransport(new EventDelivery(subscribers, owners, store, new ObjectMapper(), clock,
            Duration.ofSeconds(15), EventDeliveryScopes.ENVELOPE, tracing));
    }

    private static CanonicalOutboxMessage traced(String eventId) {
        return new CanonicalOutboxMessage(eventId, eventId, "EVENT", EVENT, "1.0.0", "CRITICAL", PUBLISHER, "TENANT",
            910001L, "910001", OffsetDateTime.parse("2026-10-28T08:59:00Z"), "correlation-1", null,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", "vendor=1", "{\"tenantId\":910001,\"memberId\":7}", 1);
    }

    private static CanonicalOutboxMessage message(String payload, String schemaVersion, int attempt) {
        return message(payload, schemaVersion, attempt, "CRITICAL");
    }

    private static CanonicalOutboxMessage message(String payload, String schemaVersion, int attempt, String reliability) {
        return new CanonicalOutboxMessage("event-1", "event-1", "EVENT", EVENT, schemaVersion, reliability, PUBLISHER,
            "TENANT", 910001L, "910001", OffsetDateTime.parse("2026-10-28T08:59:00Z"), "correlation-1", null, null, null,
            payload, attempt);
    }

    /** A clock the test moves forward, as time passes while a synchronous handler runs. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
