/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventDeliveryScopes;
import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.event.EventSubscribers;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxRelayPolicy;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * What every transport under the conformance suite delivers into: a publisher's outbox, one subscription,
 * the subscriber owner's unit of work and persistent inbox, and a handler that can be made to fail.
 * <p>The unit of work commits the inbox record and what the handler did together, or neither; a second
 * commit of the same {@code (handlerId, eventId)} fails as the inbox's unique key would (root
 * BRX-RS-MESSAGE-025).</p>
 */
public final class ConformanceWorld {

    public static final String EVENT = "MemberAccepted";
    public static final String PUBLISHER = "conformance-publisher";
    public static final String SUBSCRIBER = "conformance-subscriber";
    public static final String HANDLER = "member-accepted-projection.v1";
    public static final long TENANT = 910001L;
    public static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    /**
     * Payload the subscriber's handler declares.
     *
     * @param tenantId tenant of the member
     * @param memberId the member
     */
    public record MemberAccepted(Long tenantId, Long memberId) {
    }

    /**
     * An event the handler committed.
     *
     * @param eventId the event id
     * @param memberId the payload's member
     * @param scope the tenant scope the unit of work bound
     * @param traceparent the trace context the handler received
     * @param attempt the delivery attempt
     */
    public record Handled(String eventId, Long memberId, TenantScope scope, String traceparent, int attempt) {
    }

    private final InMemoryOutboxStore outbox = new InMemoryOutboxStore();
    private final OutboxRelayPolicy policy =
        new OutboxRelayPolicy(10, 3, Duration.ofSeconds(10), Duration.ofMillis(100), Duration.ZERO);
    private final Set<String> inbox = ConcurrentHashMap.newKeySet();
    private final List<Handled> handled = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Transaction> transaction = new ThreadLocal<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final Map<String, AtomicInteger> failuresOfEvent = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();
    private final PluginRuntimeDescriptor.EventSubscription subscription = new PluginRuntimeDescriptor.EventSubscription(
        "member-accepted", EVENT, ">=1.0.0 <2.0.0", HANDLER, "event-standard", "persistent-inbox");
    private final BoundEventHandler bound = new BoundEventHandler(SUBSCRIBER, subscription, new Projection(),
        MemberAccepted.class);
    private volatile boolean pluginsStarted = true;
    private volatile boolean failing;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T required(Supplier<T> work) {
            return inTransaction(work);
        }

        @Override
        public <T> T readOnly(Supplier<T> work) {
            return inTransaction(work);
        }
    };

    private final PersistenceCapability persistence = new PersistenceCapability() {
        @Override
        public <R> R repository(Class<R> repositoryType) {
            throw new UnsupportedOperationException("the conformance handler keeps no repository");
        }

        @Override
        public UnitOfWork unitOfWork() {
            return unitOfWork;
        }
    };

    private final EventInboxStore inboxStore = (owner, handlerId, message) -> {
        Transaction current = transaction.get();
        if (current == null) {
            throw new IllegalStateException("the inbox is written in the subscriber's unit of work");
        }
        String key = owner + "|" + handlerId + "|" + message.eventId();
        if (inbox.contains(key) || current.inbox.contains(key)) {
            duplicates.incrementAndGet();
            return false;
        }
        current.inbox.add(key);
        return true;
    };

    public InMemoryOutboxStore outbox() {
        return outbox;
    }

    /** Three attempts, 100 ms backoff per attempt, no jitter, a 10 s lease. */
    public OutboxRelayPolicy policy() {
        return policy;
    }

    /**
     * Returns a new delivery core over this world, as a host that starts builds one.
     *
     * @return the core
     */
    public EventDelivery newDelivery() {
        return new EventDelivery(subscribers(), owner -> SUBSCRIBER.equals(owner) ? Optional.of(persistence) : Optional.empty(),
            inboxStore, new ObjectMapper(), Clock.systemUTC(), Duration.ofSeconds(5), EventDeliveryScopes.ENVELOPE);
    }

    /**
     * Commits an event to the publisher's outbox, as the publisher's unit of work does.
     *
     * @param eventId event id
     * @param partitionKey partition key
     * @param tenantId tenant of a TENANT event, or {@code null} for a PLATFORM event
     * @param memberId payload member
     * @return the outbox message
     */
    public CanonicalOutboxMessage publish(String eventId, String partitionKey, Long tenantId, long memberId) {
        return publish(eventId, partitionKey, tenantId, memberId, "1.0.0");
    }

    /**
     * Commits an event of a schema version to the publisher's outbox.
     *
     * @param eventId event id
     * @param partitionKey partition key
     * @param tenantId tenant of a TENANT event, or {@code null} for a PLATFORM event
     * @param memberId payload member
     * @param schemaVersion schema version
     * @return the outbox message
     */
    public CanonicalOutboxMessage publish(String eventId, String partitionKey, Long tenantId, long memberId,
            String schemaVersion) {
        CanonicalOutboxMessage message = new CanonicalOutboxMessage("message-" + UUID.randomUUID(), eventId, "EVENT", EVENT,
            schemaVersion, "CRITICAL", PUBLISHER, tenantId == null ? "PLATFORM" : "TENANT", tenantId, partitionKey,
            OffsetDateTime.now(ZoneOffset.UTC), "correlation-" + eventId, null, TRACEPARENT, "conformance=1",
            "{\"tenantId\":" + tenantId + ",\"memberId\":" + memberId + "}", 0);
        outbox.append(message);
        return message;
    }

    /** Makes the plugins started or not; until they start no event can be judged or delivered. */
    public void pluginsStarted(boolean started) {
        pluginsStarted = started;
    }

    /** Makes the handler fail on its next calls. */
    public void failNext(int count) {
        failuresLeft.set(count);
    }

    /** Makes the handler fail on the next deliveries of one event. */
    public void failNextOf(String eventId, int count) {
        failuresOfEvent.put(eventId, new AtomicInteger(count));
    }

    /** Makes the handler fail until told otherwise. */
    public void failing(boolean failing) {
        this.failing = failing;
    }

    /** The events the handler committed, in commit order. */
    public List<Handled> handled() {
        return List.copyOf(handled);
    }

    /** The ids of the events the handler committed, in commit order. */
    public List<String> handledIds() {
        return handled.stream().map(Handled::eventId).toList();
    }

    /** How often the handler was called, committed or not. */
    public int calls() {
        return calls.get();
    }

    /** How often the inbox skipped an event the handler had committed. */
    public int duplicates() {
        return duplicates.get();
    }

    private EventSubscribers subscribers() {
        return new EventSubscribers() {
            @Override
            public boolean ready() {
                return pluginsStarted;
            }

            @Override
            public Optional<String> publisherOf(String eventType) {
                return EVENT.equals(eventType) ? Optional.of(PUBLISHER) : Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return EVENT.equals(eventType) ? subscriptions() : List.of();
            }

            @Override
            public List<DeclaredSubscriber> subscriptions() {
                return List.of(new DeclaredSubscriber(SUBSCRIBER, subscription, bound));
            }

            @Override
            public Set<String> publishedEventTypes() {
                return Set.of(EVENT);
            }
        };
    }

    private <T> T inTransaction(Supplier<T> work) {
        if (transaction.get() != null) {
            return work.get();
        }
        Transaction current = new Transaction();
        transaction.set(current);
        try {
            T result = work.get();
            commit(current);
            return result;
        } finally {
            transaction.remove();
        }
    }

    private void commit(Transaction current) {
        List<String> added = new ArrayList<>();
        for (String key : current.inbox) {
            if (!inbox.add(key)) {
                inbox.removeAll(added);
                throw new IllegalStateException("duplicate inbox key " + key);
            }
            added.add(key);
        }
        handled.addAll(current.handled);
    }

    private final class Projection implements EventHandler<MemberAccepted> {

        @Override
        public CompletionStage<Void> handle(EventInvocation<MemberAccepted> invocation) {
            calls.incrementAndGet();
            AtomicInteger ofEvent = failuresOfEvent.get(invocation.event().eventId());
            boolean eventFails = ofEvent != null && ofEvent.getAndUpdate(left -> Math.max(0, left - 1)) > 0;
            if (failing || eventFails || failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                throw new IllegalStateException("the projection is failing");
            }
            transaction.get().handled.add(new Handled(invocation.event().eventId(), invocation.event().payload().memberId(),
                TenantScopes.requested(), invocation.trace().traceparent(), invocation.deliveryAttempt()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class Transaction {
        private final List<String> inbox = new ArrayList<>();
        private final List<Handled> handled = new ArrayList<>();
    }
}
