/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransport;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.PublisherDecision;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Delivers a claimed outbox event to its subscribers in this process (P4 design D11, §7.3).
 *
 * <p>Each subscriber handles the event in its own owner's unit of work, after the owner's persistent
 * inbox has recorded {@code (handlerId, eventId)} in the same transaction (root BRX-RS-MESSAGE-025).
 * A TENANT event binds its envelope's tenant; a PLATFORM event binds no tenant. The relay marks the
 * outbox row published only when this method returns, that is once every declared subscriber has
 * committed the event or had already committed it; a failed subscriber makes the whole message wait
 * for a retry, and the inbox keeps the others from handling it twice.</p>
 *
 * <p>A message is parked at once (a permanent failure) when its publisher is not the plugin that
 * declares the event type, when a subscriber does not accept its schema version, or when its payload
 * cannot be decoded. A subscriber that is declared but not started makes the message wait.</p>
 */
public final class InProcessEventTransport implements OutboxTransport {

    static final String KIND_UNSUPPORTED = "OUTBOX_KIND_UNSUPPORTED";
    static final String PUBLISHER_REJECTED = "EVENT_PUBLISHER_REJECTED";
    static final String SCHEMA_UNSUPPORTED = "EVENT_SCHEMA_UNSUPPORTED";
    static final String PAYLOAD_INVALID = "EVENT_PAYLOAD_INVALID";
    static final String SUBSCRIBER_NOT_READY = "EVENT_SUBSCRIBER_NOT_READY";
    static final String HANDLER_FAILED = "EVENT_HANDLER_FAILED";

    private static final Logger log = LoggerFactory.getLogger(InProcessEventTransport.class);

    private final EventSubscribers subscribers;
    private final OwnerPersistenceProvider owners;
    private final EventInboxStore inbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration deadline;
    private volatile boolean stopped;

    /**
     * Creates the transport.
     *
     * @param subscribers who publishes and subscribes to each event type
     * @param owners each subscriber's owner persistence
     * @param inbox the owners' persistent inboxes
     * @param json decodes payloads
     * @param clock delivery clock
     * @param deadline how long a handler may take; shorter than the relay's lease
     */
    public InProcessEventTransport(
            EventSubscribers subscribers,
            OwnerPersistenceProvider owners,
            EventInboxStore inbox,
            ObjectMapper json,
            Clock clock,
            Duration deadline) {
        this.subscribers = Objects.requireNonNull(subscribers, "subscribers must not be null");
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.inbox = Objects.requireNonNull(inbox, "inbox must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.deadline = Objects.requireNonNull(deadline, "deadline must not be null");
    }

    /** Tells handlers still running that the runtime no longer waits for them. */
    public void stop() {
        stopped = true;
    }

    @Override
    public void publish(CanonicalOutboxMessage message) {
        if (!"EVENT".equals(message.messageKind())) {
            throw permanent(KIND_UNSUPPORTED, "only events are delivered in process, not " + message.messageKind(), null);
        }
        String eventType = message.messageType();
        String publisher = subscribers.publisherOf(eventType).orElse(null);
        if (!message.producerPluginId().equals(publisher)) {
            throw permanent(PUBLISHER_REJECTED, eventType + " is not published by " + message.producerPluginId(), null);
        }
        List<EventSubscribers.DeclaredSubscriber> declared = subscribers.subscribersOf(eventType);
        for (EventSubscribers.DeclaredSubscriber subscriber : declared) {
            if (!BrixRange.contains(message.schemaVersion(), subscriber.subscription().schemaRange())) {
                throw permanent(SCHEMA_UNSUPPORTED, subscriber.subscription().handlerId() + " does not accept "
                    + eventType + " " + message.schemaVersion(), null);
            }
            if (subscriber.handler() == null) {
                throw new OutboxTransportException(SUBSCRIBER_NOT_READY,
                    subscriber.subscription().handlerId() + " of " + subscriber.pluginId() + " is not started", null);
            }
        }
        for (EventSubscribers.DeclaredSubscriber subscriber : declared) {
            deliver(message, subscriber.handler());
        }
    }

    private void deliver(CanonicalOutboxMessage message, BoundEventHandler bound) {
        EventInvocation<Object> invocation = invocation(message, bound);
        @SuppressWarnings("unchecked")
        EventHandler<Object> handler = (EventHandler<Object>) bound.handler();
        String handlerId = bound.subscription().handlerId();
        PersistenceCapability persistence = owners.forOwner(bound.pluginId())
            .orElseThrow(() -> new OutboxTransportException(SUBSCRIBER_NOT_READY,
                bound.pluginId() + " has no persistence unit for its inbox", null));
        TenantScope scope = "TENANT".equals(message.scope()) ? TenantScope.tenant(message.tenantId()) : TenantScope.none();
        try {
            boolean handled = TenantScopes.within(scope, () -> persistence.unitOfWork().required(() -> {
                if (!inbox.insertProcessed(bound.pluginId(), handlerId, message)) {
                    return false;
                }
                await(handler.handle(invocation));
                return true;
            }));
            log.info("event=event.delivered type={} event_id={} handler={} owner={} outcome={} attempt={}",
                message.messageType(), message.eventId(), handlerId, bound.pluginId(), handled ? "handled" : "duplicate",
                invocation.deliveryAttempt());
        } catch (RuntimeException failure) {
            log.warn("event=event.delivery_failed type={} event_id={} handler={} owner={} attempt={}",
                message.messageType(), message.eventId(), handlerId, bound.pluginId(), invocation.deliveryAttempt(), failure);
            throw new OutboxTransportException(HANDLER_FAILED, handlerId + " failed to handle " + message.eventId(), failure);
        }
    }

    private EventInvocation<Object> invocation(CanonicalOutboxMessage message, BoundEventHandler bound) {
        Object payload;
        try {
            payload = json.readValue(message.payload(), bound.payloadType());
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw permanent(PAYLOAD_INVALID, message.messageType() + " payload does not decode to "
                + bound.payloadType().getSimpleName(), invalid);
        }
        String traceparent = message.traceparent() == null || message.traceparent().isBlank()
            ? newTraceparent()
            : message.traceparent();
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
            new TraceContext(traceparent, message.tracestate()), now.plus(deadline), () -> stopped,
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

    private static void await(CompletionStage<Void> handled) {
        try {
            handled.toCompletableFuture().join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private static OutboxTransportException permanent(String code, String message, Throwable cause) {
        return new OutboxTransportException(code, message, cause, true);
    }
}
