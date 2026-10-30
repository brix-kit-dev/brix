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

import io.runtime.sdk.plugin.CommandInvocation;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.command.CommandProviders;
import io.runtime.orchestrator.command.CommandMessages;
import io.runtime.orchestrator.command.CommandDelivery;
import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventDeliveryScopes;
import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.event.EventSubscribers;
import io.runtime.orchestrator.outbox.CanonicalMessagingTables;
import io.runtime.orchestrator.outbox.OutboxEndpoint;
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
 * BRX-RS-MESSAGE-025). {@link #withoutInbox()} makes a world whose subscription keeps no inbox.</p>
 */
public final class ConformanceWorld {

    public static final String EVENT = "MemberAccepted";
    public static final String PUBLISHER = "conformance-publisher";
    public static final String SUBSCRIBER = "conformance-subscriber";
    public static final String HANDLER = "member-accepted-projection.v1";
    public static final long TENANT = 910001L;
    /** The publisher's outbox table, as the management endpoint names it. */
    public static final String OUTBOX = "conformance_publisher_outbox";
    public static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    /** The command the publisher submits and the subscriber provides (Kafka design §14 K22). */
    public static final String COMMAND = "ReserveStock";
    public static final String COMMAND_HANDLER = "reserve-stock.v1";

    /** The payload of {@link #COMMAND}. */
    public record Reserve(String sku) {
    }

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
    private final RecordingMessageAudit audit = new RecordingMessageAudit();
    private final RecordingMessageTracing tracing = new RecordingMessageTracing();
    private final OutboxRelayPolicy policy =
        new OutboxRelayPolicy(10, 3, Duration.ofSeconds(10), Duration.ofMillis(100), Duration.ZERO);
    private final Set<String> inbox = ConcurrentHashMap.newKeySet();
    private final List<Handled> handled = new CopyOnWriteArrayList<>();
    private final List<String> handledCommands = new CopyOnWriteArrayList<>();
    private final Set<String> commandKeys = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Transaction> transaction = new ThreadLocal<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final Map<String, AtomicInteger> failuresOfEvent = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();
    private final PluginRuntimeDescriptor.EventSubscription subscription;
    private final BoundEventHandler bound;
    private final String reliability;
    private volatile boolean pluginsStarted = true;
    private volatile boolean failing;
    private volatile Error nextError;
    private volatile boolean hanging;

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

    /** A world whose event type is CRITICAL and whose subscriber records each event in its persistent inbox. */
    public ConformanceWorld() {
        this(PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX, "CRITICAL");
    }

    private ConformanceWorld(String idempotencyPolicy, String reliability) {
        this.subscription = new PluginRuntimeDescriptor.EventSubscription(
            "member-accepted", EVENT, ">=1.0.0 <2.0.0", HANDLER, "event-standard", idempotencyPolicy);
        this.bound = new BoundEventHandler(SUBSCRIBER, subscription, new Projection(), MemberAccepted.class);
        this.reliability = reliability;
    }

    /**
     * Returns a world whose event type is BEST_EFFORT and whose subscription keeps no inbox (root
     * BRX-RS-MANIFEST-025). The subscriber owns no persistence, so its handler runs outside any unit of work.
     *
     * @return the world
     */
    public static ConformanceWorld withoutInbox() {
        return new ConformanceWorld(PluginRuntimeDescriptor.EventSubscription.NO_DURABLE_IDEMPOTENCY, "BEST_EFFORT");
    }

    public InMemoryOutboxStore outbox() {
        return outbox;
    }

    /** The platform audit of operators' replays, discards and republishes (Kafka design D33). */
    public RecordingMessageAudit audit() {
        return audit;
    }

    /**
     * Returns the management endpoint over the publisher's outbox, recording in {@link #audit()}, with the default
     * outbox retention.
     *
     * @return the endpoint
     */
    public OutboxEndpoint outboxEndpoint() {
        return new OutboxEndpoint(() -> () -> Map.of(OUTBOX, outbox), () -> audit,
            CanonicalMessagingTables.OUTBOX_RETENTION, Clock.systemUTC());
    }

    /** The spans the relay and the delivery core start, which the transport's harness hands its relay. */
    public RecordingMessageTracing tracing() {
        return tracing;
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
        return newDelivery(Duration.ofSeconds(5));
    }

    /**
     * Returns a new delivery core whose handlers have the given deadline.
     *
     * @param deadline how long a handler may take
     * @return the core
     */
    public EventDelivery newDelivery(Duration deadline) {
        return new EventDelivery(subscribers(),
            owner -> SUBSCRIBER.equals(owner) && subscription.persistentInbox() ? Optional.of(persistence) : Optional.empty(),
            inboxStore, new ObjectMapper(), Clock.systemUTC(), deadline, EventDeliveryScopes.ENVELOPE, tracing)
            .deliveringCommandsWith(new CommandDelivery(commandProviders(),
                owner -> SUBSCRIBER.equals(owner) ? Optional.of(persistence) : Optional.empty(), inboxStore,
                (owner, handlerId, message, scope, key) -> {
                    Transaction current = transaction.get();
                    if (current == null) {
                        throw new IllegalStateException("the idempotency record is written in the provider's unit of work");
                    }
                    String taken = owner + "|" + handlerId + "|" + scope + "|" + key;
                    if (commandKeys.contains(taken) || current.commandKeys.contains(taken)) {
                        return false;
                    }
                    current.commandKeys.add(taken);
                    return true;
                },
                new ObjectMapper(), Clock.systemUTC(), Duration.ofSeconds(5), tracing));
    }

    /**
     * Commits a command the publisher submits to its outbox, as its unit of work does (Kafka design §14 K22).
     *
     * @param commandId command id
     * @param idempotencyKey business key, also its partition key
     * @return the outbox message
     */
    public CanonicalOutboxMessage submit(String commandId, String idempotencyKey) {
        CanonicalOutboxMessage message = new CanonicalOutboxMessage(commandId, commandId, "COMMAND", COMMAND, "1.0.0",
            "STANDARD", PUBLISHER, "PLATFORM", null, idempotencyKey, OffsetDateTime.now(ZoneOffset.UTC),
            "correlation-" + commandId, null, TRACEPARENT, "conformance=1",
            CommandMessages.encode(new ObjectMapper(), new Reserve("sku-" + idempotencyKey), "order", idempotencyKey), 0);
        outbox.append(message);
        return message;
    }

    /**
     * Returns the ids of the commands the provider's handler committed, in order.
     *
     * @return command ids
     */
    public List<String> handledCommands() {
        return List.copyOf(handledCommands);
    }

    /**
     * Whether a committed delivery of the message is recorded in the subscriber's inbox.
     *
     * @param messageId event or command id
     * @return whether it is recorded
     */
    public boolean inboxed(String messageId) {
        return inbox.stream().anyMatch(key -> key.endsWith("|" + messageId));
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
            schemaVersion, reliability, PUBLISHER, tenantId == null ? "PLATFORM" : "TENANT", tenantId, partitionKey,
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

    /** Makes the handler throw this error once, as a handler whose class cannot load does. */
    public void failNextWith(Error error) {
        this.nextError = error;
    }

    /** Makes the handler return a stage that never completes, as a handler that hangs does. */
    public void hanging(boolean hanging) {
        this.hanging = hanging;
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

    private CommandProviders commandProviders() {
        return new CommandProviders() {
            @Override
            public boolean ready() {
                return pluginsStarted;
            }

            @Override
            public Optional<PluginRuntimeManager.CommandProvider> providerOf(String commandType) {
                return COMMAND.equals(commandType) ? Optional.of(provider()) : Optional.empty();
            }

            @Override
            public List<PluginRuntimeManager.CommandProvider> providers() {
                return List.of(provider());
            }

            @Override
            public Optional<PluginRuntimeDescriptor.ConsumedCommand> consumed(String pluginId, String commandType) {
                return PUBLISHER.equals(pluginId) && COMMAND.equals(commandType)
                    ? Optional.of(new PluginRuntimeDescriptor.ConsumedCommand(COMMAND, ">=1.0.0 <2.0.0", true))
                    : Optional.empty();
            }

            private PluginRuntimeManager.CommandProvider provider() {
                return new PluginRuntimeManager.CommandProvider(SUBSCRIBER,
                    new PluginRuntimeDescriptor.ProvidedCommand(COMMAND, COMMAND_HANDLER, "1.0.0"), new Stock(),
                    Reserve.class);
            }
        };
    }

    private final class Stock implements CommandHandler<Reserve> {

        @Override
        public CompletionStage<Void> handle(CommandInvocation<Reserve> invocation) {
            Transaction current = transaction.get();
            if (current == null) {
                throw new IllegalStateException("a command runs in its provider's unit of work");
            }
            current.commands.add(invocation.command().commandId());
            return CompletableFuture.completedFuture(null);
        }
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
        commandKeys.addAll(current.commandKeys);
        handledCommands.addAll(current.commands);
    }

    private final class Projection implements EventHandler<MemberAccepted> {

        @Override
        public CompletionStage<Void> handle(EventInvocation<MemberAccepted> invocation) {
            calls.incrementAndGet();
            Error error = nextError;
            if (error != null) {
                nextError = null;
                throw error;
            }
            if (hanging) {
                return new CompletableFuture<>();
            }
            AtomicInteger ofEvent = failuresOfEvent.get(invocation.event().eventId());
            boolean eventFails = ofEvent != null && ofEvent.getAndUpdate(left -> Math.max(0, left - 1)) > 0;
            if (failing || eventFails || failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                throw new IllegalStateException("the projection is failing");
            }
            Handled event = new Handled(invocation.event().eventId(), invocation.event().payload().memberId(),
                TenantScopes.requested(), invocation.trace().traceparent(), invocation.deliveryAttempt());
            Transaction current = transaction.get();
            if (current == null) {
                // Without a unit of work there is nothing to commit with.
                handled.add(event);
            } else {
                current.handled.add(event);
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class Transaction {
        private final List<String> inbox = new ArrayList<>();
        private final List<Handled> handled = new ArrayList<>();
        private final List<String> commandKeys = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();
    }
}
