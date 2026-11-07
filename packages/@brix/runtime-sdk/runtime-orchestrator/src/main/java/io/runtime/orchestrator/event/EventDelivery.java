/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.command.CommandDelivery;
import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.PublisherDecision;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Delivers an event to its subscribers, whichever transport carried it (Kafka design §4.1; P4 design §7.3).
 * <p>Each subscriber handles the event in its own owner's unit of work, after the owner's persistent
 * inbox has recorded {@code (handlerId, eventId)} in the same transaction (root BRX-RS-MESSAGE-025). The unit of work
 * lasts no longer than the time left before the delivery's deadline, and a handler that finishes after the deadline
 * rolls it back ({@link DeliveryDeadline}).
 * A subscription that keeps no inbox takes BEST_EFFORT events only and handles each delivery, in its owner's
 * unit of work when the owner has one (MANIFEST-025). A TENANT event binds its envelope's tenant; a PLATFORM event binds no tenant, unless the
 * subscription names a registered cross-tenant policy, which {@link EventDeliveryScopes} binds (P4
 * design §4.3).</p>
 * <p>A delivery fails for good ({@link EventDeliveryException#permanent()}) when its publisher is not the
 * plugin that declares the event type, when the subscriber does not accept its schema version or, keeping no
 * inbox, its reliability, or when its payload cannot be decoded. A subscriber that is declared but not started, a handler that fails, and every
 * event while the plugins have not started make the event wait for a retry.</p>
 */
public final class EventDelivery {

    /** A command reached a host that delivers no commands. */
    public static final String KIND_UNSUPPORTED = "OUTBOX_KIND_UNSUPPORTED";
    public static final String PUBLISHER_REJECTED = "EVENT_PUBLISHER_REJECTED";
    public static final String SCHEMA_UNSUPPORTED = "EVENT_SCHEMA_UNSUPPORTED";
    /** A CRITICAL or STANDARD event reached a subscription that keeps no inbox. */
    public static final String RELIABILITY_UNSUPPORTED = "EVENT_RELIABILITY_UNSUPPORTED";
    public static final String PAYLOAD_INVALID = "EVENT_PAYLOAD_INVALID";
    public static final String SUBSCRIBER_NOT_READY = "EVENT_SUBSCRIBER_NOT_READY";
    /** The event reached a subscription its type does not have, for example one an upgrade removed. */
    public static final String SUBSCRIPTION_UNKNOWN = "EVENT_SUBSCRIPTION_UNKNOWN";
    public static final String HANDLER_FAILED = "EVENT_HANDLER_FAILED";

    private static final Logger log = LoggerFactory.getLogger(EventDelivery.class);

    private final EventSubscribers subscribers;
    private final OwnerPersistenceProvider owners;
    private final EventInboxStore inbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration deadline;
    private final EventDeliveryScopes scopes;
    private final MessageTracing tracing;
    private volatile CommandDelivery commands;
    private volatile boolean stopped;

    /**
     * Creates the delivery core.
     *
     * @param subscribers who publishes and subscribes to each event type
     * @param owners each subscriber's owner persistence
     * @param inbox the owners' persistent inboxes
     * @param json decodes payloads
     * @param clock delivery clock
     * @param deadline how long a handler may take; shorter than the relay's lease
     * @param scopes the scope of each delivery
     */
    public EventDelivery(
            EventSubscribers subscribers,
            OwnerPersistenceProvider owners,
            EventInboxStore inbox,
            ObjectMapper json,
            Clock clock,
            Duration deadline,
            EventDeliveryScopes scopes) {
        this(subscribers, owners, inbox, json, clock, deadline, scopes, MessageTracing.NOOP);
    }

    /**
     * Creates the delivery core, which handles each delivery in its CONSUMER span (root BRX-RS-OTEL-002).
     *
     * @param subscribers who publishes and subscribes to each event type
     * @param owners each subscriber's owner persistence
     * @param inbox the owners' persistent inboxes
     * @param json decodes payloads
     * @param clock delivery clock
     * @param deadline how long a handler may take; shorter than the relay's lease
     * @param scopes the scope of each delivery
     * @param tracing the spans of the deliveries
     */
    public EventDelivery(
            EventSubscribers subscribers,
            OwnerPersistenceProvider owners,
            EventInboxStore inbox,
            ObjectMapper json,
            Clock clock,
            Duration deadline,
            EventDeliveryScopes scopes,
            MessageTracing tracing) {
        this.subscribers = Objects.requireNonNull(subscribers, "subscribers must not be null");
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.inbox = Objects.requireNonNull(inbox, "inbox must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.deadline = Objects.requireNonNull(deadline, "deadline must not be null");
        this.scopes = Objects.requireNonNull(scopes, "scopes must not be null");
        this.tracing = Objects.requireNonNull(tracing, "tracing must not be null");
    }

    /**
     * Whether events can be delivered: the plugins have started, so every publisher and subscriber is known.
     *
     * @return readiness
     */
    public boolean ready() {
        return subscribers.ready();
    }

    /** Tells handlers still running, through their cancellation signal, that the runtime no longer waits for them. */
    public void stop() {
        stopped = true;
        CommandDelivery commandDelivery = commands;
        if (commandDelivery != null) {
            commandDelivery.stop();
        }
    }

    /**
     * Delivers commands too, through the given core (Kafka design §14 K22): the transports, consumers and replays
     * that hand messages here then serve commands as they serve events.
     *
     * @param commandDelivery the command delivery core
     * @return this delivery core
     */
    public EventDelivery deliveringCommandsWith(CommandDelivery commandDelivery) {
        this.commands = Objects.requireNonNull(commandDelivery, "commandDelivery must not be null");
        return this;
    }

    /**
     * Records that an operator discarded a command a provider's consumer had parked, so that its sender reads it as
     * discarded (standard-link plan point 7). A core that serves no commands records nothing.
     *
     * @param pluginId the providing plugin
     * @param handlerId the provider's handler id
     * @param message the parked command
     * @param errorCode why it was parked
     * @throws RuntimeException when the provider cannot record it now
     */
    public void discardedCommand(String pluginId, String handlerId, CanonicalOutboxMessage message, String errorCode) {
        CommandDelivery commandDelivery = commands;
        if (commandDelivery != null) {
            commandDelivery.discarded(pluginId, handlerId, message, errorCode);
        }
    }

    /**
     * Returns one consumed subscription per provided command, empty when this core serves no commands.
     *
     * @return the command subscriptions
     */
    public List<ConsumedSubscription> commandProvisions() {
        CommandDelivery commandDelivery = commands;
        return commandDelivery == null ? List.of() : commandDelivery.provisions();
    }

    /**
     * Returns every declared subscription, for a transport that consumes per subscription.
     *
     * @return the subscriptions
     */
    public List<EventSubscribers.DeclaredSubscriber> subscriptions() {
        return subscribers.subscriptions();
    }

    /**
     * Returns the event types the plugins publish.
     *
     * @return the published event types
     */
    public Set<String> publishedEventTypes() {
        return subscribers.publishedEventTypes();
    }

    /**
     * Delivers an event to every subscriber of its type, as the in-process transport does. Every subscriber
     * is judged before any handles the event, so an event one of them cannot accept reaches none.
     *
     * @param message the event
     * @throws EventDeliveryException when a subscriber did not commit the event
     */
    public void deliverToAll(CanonicalOutboxMessage message) {
        if (isCommand(message)) {
            commandsFor(message).deliver(message);
            return;
        }
        List<EventSubscribers.DeclaredSubscriber> declared = subscribers.subscribersOf(admit(message));
        for (EventSubscribers.DeclaredSubscriber subscriber : declared) {
            accept(message, subscriber);
        }
        for (EventSubscribers.DeclaredSubscriber subscriber : declared) {
            deliver(message, subscriber.handler());
        }
    }

    /**
     * Delivers an event to one subscription, as a broker's consumer for that subscription, or a replay, does.
     *
     * @param pluginId the subscriber plugin
     * @param handlerId the subscription's handler id
     * @param message the event
     * @throws EventDeliveryException when the subscriber did not commit the event
     */
    public void deliverTo(String pluginId, String handlerId, CanonicalOutboxMessage message) {
        if (isCommand(message)) {
            commandsFor(message).deliverTo(pluginId, handlerId, message);
            return;
        }
        String eventType = admit(message);
        EventSubscribers.DeclaredSubscriber subscriber = subscribers.subscribersOf(eventType).stream()
            .filter(declared -> declared.pluginId().equals(pluginId) && declared.subscription().handlerId().equals(handlerId))
            .findFirst()
            .orElseThrow(() -> permanent(SUBSCRIPTION_UNKNOWN,
                pluginId + " has no subscription " + handlerId + " to " + eventType, null));
        accept(message, subscriber);
        deliver(message, subscriber.handler());
    }

    private static boolean isCommand(CanonicalOutboxMessage message) {
        return "COMMAND".equals(message.messageKind());
    }

    private CommandDelivery commandsFor(CanonicalOutboxMessage message) {
        CommandDelivery commandDelivery = commands;
        if (commandDelivery == null) {
            throw permanent(KIND_UNSUPPORTED, "this host delivers no commands, so not " + message.messageType(), null);
        }
        return commandDelivery;
    }

    /** Admits an event its declared publisher sent, once the plugins have started; returns its type. */
    private String admit(CanonicalOutboxMessage message) {
        if (!"EVENT".equals(message.messageKind())) {
            throw permanent(KIND_UNSUPPORTED, "only events are delivered, not " + message.messageKind(), null);
        }
        if (!subscribers.ready()) {
            // Judging the publisher now would park every event that was pending when the host started.
            throw new EventDeliveryException(SUBSCRIBER_NOT_READY, "the plugins have not started", null, false);
        }
        String eventType = message.messageType();
        String publisher = subscribers.publisherOf(eventType).orElse(null);
        if (!message.producerPluginId().equals(publisher)) {
            throw permanent(PUBLISHER_REJECTED, eventType + " is not published by " + message.producerPluginId(), null);
        }
        return eventType;
    }

    private static void accept(CanonicalOutboxMessage message, EventSubscribers.DeclaredSubscriber subscriber) {
        if (!BrixRange.contains(message.schemaVersion(), subscriber.subscription().schemaRange())) {
            throw permanent(SCHEMA_UNSUPPORTED, subscriber.subscription().handlerId() + " does not accept "
                + message.messageType() + " " + message.schemaVersion(), null);
        }
        if (!subscriber.subscription().persistentInbox()
                && !EventReliability.BEST_EFFORT.name().equalsIgnoreCase(message.reliability())) {
            throw permanent(RELIABILITY_UNSUPPORTED, subscriber.subscription().handlerId() + " keeps no inbox, so it takes"
                + " BEST_EFFORT events only, not " + message.messageType() + " " + message.reliability(), null);
        }
        if (subscriber.handler() == null) {
            throw new EventDeliveryException(SUBSCRIBER_NOT_READY,
                subscriber.subscription().handlerId() + " of " + subscriber.pluginId() + " is not started", null, false);
        }
    }

    /** Handles the delivery in its CONSUMER span, a child of the context the message carries. */
    private void deliver(CanonicalOutboxMessage message, BoundEventHandler bound) {
        try (MessageTracing.MessageSpan span = tracing.consumer(message, bound.pluginId() + "."
                + bound.subscription().handlerId())) {
            try {
                deliver(message, bound, span);
            } catch (RuntimeException failure) {
                span.failed(failure);
                throw failure;
            }
        }
    }

    private void deliver(CanonicalOutboxMessage message, BoundEventHandler bound, MessageTracing.MessageSpan span) {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        EventInvocation<Object> invocation = invocation(message, bound, span, cancelled);
        @SuppressWarnings("unchecked")
        EventHandler<Object> handler = (EventHandler<Object>) bound.handler();
        String handlerId = bound.subscription().handlerId();
        boolean keepsInbox = bound.subscription().persistentInbox();
        Optional<PersistenceCapability> persistence = owners.forOwner(bound.pluginId());
        if (keepsInbox && persistence.isEmpty()) {
            throw new EventDeliveryException(SUBSCRIBER_NOT_READY,
                bound.pluginId() + " has no persistence unit for its inbox", null, false);
        }
        List<Long> reached = new ArrayList<>();
        EventDeliveryScope scope = null;
        try {
            scope = scopes.scopeOf(bound, message, invocation.event().payload());
            EventDeliveryScope bind = scope;
            Supplier<Boolean> handle = () -> {
                try {
                    if (keepsInbox && !inbox.insertProcessed(bound.pluginId(), handlerId, message)) {
                        return false;
                    }
                    // Typed queries the handler makes run as this subscriber, in the event's tenant (root
                    // BRX-RS-MANIFEST-028).
                    RuntimeInvocations.within(RuntimeInvocation.forEvent(bound.pluginId(), invocation), () -> {
                        DeliveryDeadline.await(handler.handle(invocation), clock, invocation.deadline(), cancelled);
                        return null;
                    });
                    bind.handled(TenantScopes.bound());
                    return true;
                } finally {
                    reached.addAll(TenantScopes.bound());
                }
            };
            boolean handled = TenantScopes.within(scope.tenantScope(), () -> DeliveryDeadline.inUnitOfWork(owners,
                bound.pluginId(), clock, invocation.deadline(), handle));
            log.info("event=event.delivered type={} event_id={} handler={} owner={} outcome={} attempt={}",
                message.messageType(), message.eventId(), handlerId, bound.pluginId(), handled ? "handled" : "duplicate",
                invocation.deliveryAttempt());
            if (handled) {
                scope.committed(List.copyOf(reached));
            }
        } catch (RuntimeException | Error thrown) {
            // An Error from a handler, such as a class that cannot load or a failed assertion, fails this delivery like
            // an exception: its unit of work has rolled back, and the transport retries it (Kafka design §14 K10).
            Failures.rethrowFatal(thrown);
            RuntimeException failure = thrown instanceof RuntimeException exception ? exception
                : new IllegalStateException(handlerId + " threw " + thrown, thrown);
            if (scope != null) {
                scope.failed(List.copyOf(reached), failure);
            }
            log.warn("event=event.delivery_failed type={} event_id={} handler={} owner={} attempt={}",
                message.messageType(), message.eventId(), handlerId, bound.pluginId(), invocation.deliveryAttempt(), failure);
            throw EventDeliveryException.whileHandling(HANDLER_FAILED, handlerId + " failed to handle " + message.eventId(),
                failure);
        }
    }

    private EventInvocation<Object> invocation(CanonicalOutboxMessage message, BoundEventHandler bound,
            MessageTracing.MessageSpan span, java.util.concurrent.atomic.AtomicBoolean cancelled) {
        Object payload;
        try {
            payload = MessagePayloads.read(json, message.payload(), bound.payloadType());
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw permanent(PAYLOAD_INVALID, message.messageType() + " payload does not decode to "
                + bound.payloadType().getSimpleName(), invalid);
        }
        String traceparent = span.traceparent() == null || span.traceparent().isBlank()
            ? newTraceparent()
            : span.traceparent();
        EventEnvelope<Object> envelope = new EventEnvelope<>(
            message.eventId(),
            message.messageType(),
            message.schemaVersion(),
            message.occurredAt().toInstant(),
            message.producerPluginId(),
            EventScope.valueOf(message.scope().toUpperCase(Locale.ROOT)),
            message.tenantId() == null ? null : String.valueOf(message.tenantId()),
            message.correlationId() == null || message.correlationId().isBlank() ? message.eventId() : message.correlationId(),
            message.causationId(),
            traceparent,
            message.partitionKey() == null || message.partitionKey().isBlank() ? message.eventId() : message.partitionKey(),
            payload,
            EventReliability.valueOf(message.reliability().toUpperCase(Locale.ROOT)));
        Instant now = clock.instant();
        return new EventInvocation<>(envelope, new PublisherDecision(message.producerPluginId(), null),
            new TraceContext(traceparent, span.tracestate()), now.plus(deadline), () -> stopped || cancelled.get(),
            Math.max(1, message.attemptCount()));
    }

    private static String newTraceparent() {
        HexFormat hex = HexFormat.of();
        byte[] trace = new byte[16];
        byte[] span = new byte[8];
        ThreadLocalRandom.current().nextBytes(trace);
        ThreadLocalRandom.current().nextBytes(span);
        trace[0] |= 1;
        span[0] |= 1;
        return "00-" + hex.formatHex(trace) + "-" + hex.formatHex(span) + "-01";
    }

    private static EventDeliveryException permanent(String code, String message, Throwable cause) {
        return new EventDeliveryException(code, message, cause, true);
    }
}
