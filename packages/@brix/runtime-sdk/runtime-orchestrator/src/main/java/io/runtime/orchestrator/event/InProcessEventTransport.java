/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransport;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;

/**
 * Delivers a claimed outbox event to its subscribers in this process (P4 design D11, §7.3), through the
 * delivery core every transport shares (Kafka design §4.1).
 * <p>The relay marks the outbox row published only when this method returns, that is once every declared
 * subscriber has committed the event or had already committed it; a failed subscriber makes the whole
 * message wait for a retry, and the inbox keeps the others from handling it twice. A failure the core
 * judges permanent parks the message at once. Every message waits while the plugins have not started:
 * the transport is not {@link #ready()} then.</p>
 */
public final class InProcessEventTransport implements OutboxTransport {

    static final String KIND_UNSUPPORTED = EventDelivery.KIND_UNSUPPORTED;
    static final String PUBLISHER_REJECTED = EventDelivery.PUBLISHER_REJECTED;
    static final String SCHEMA_UNSUPPORTED = EventDelivery.SCHEMA_UNSUPPORTED;
    static final String RELIABILITY_UNSUPPORTED = EventDelivery.RELIABILITY_UNSUPPORTED;
    static final String PAYLOAD_INVALID = EventDelivery.PAYLOAD_INVALID;
    static final String SUBSCRIBER_NOT_READY = EventDelivery.SUBSCRIBER_NOT_READY;
    static final String HANDLER_FAILED = EventDelivery.HANDLER_FAILED;

    private final EventDelivery delivery;

    /**
     * Creates the transport over the host's delivery core.
     *
     * @param delivery the delivery core
     */
    public InProcessEventTransport(EventDelivery delivery) {
        this.delivery = Objects.requireNonNull(delivery, "delivery must not be null");
    }

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
        this(subscribers, owners, inbox, json, clock, deadline, EventDeliveryScopes.ENVELOPE);
    }

    /**
     * Creates the transport with the scopes of the registered cross-tenant policies.
     *
     * @param subscribers who publishes and subscribes to each event type
     * @param owners each subscriber's owner persistence
     * @param inbox the owners' persistent inboxes
     * @param json decodes payloads
     * @param clock delivery clock
     * @param deadline how long a handler may take; shorter than the relay's lease
     * @param scopes the scope of each delivery
     */
    public InProcessEventTransport(
            EventSubscribers subscribers,
            OwnerPersistenceProvider owners,
            EventInboxStore inbox,
            ObjectMapper json,
            Clock clock,
            Duration deadline,
            EventDeliveryScopes scopes) {
        this(new EventDelivery(subscribers, owners, inbox, json, clock, deadline, scopes));
    }

    /** Ready once the plugins have started; before that no publisher or subscriber is known. */
    @Override
    public boolean ready() {
        return delivery.ready();
    }

    /** Tells handlers still running, through their cancellation signal, that the runtime no longer waits for them. */
    @Override
    public void stop() {
        delivery.stop();
    }

    @Override
    public void publish(CanonicalOutboxMessage message) {
        try {
            delivery.deliverToAll(message);
        } catch (EventDeliveryException failure) {
            if (SubscriptionDelivery.unavailable(failure)) {
                // Not the event's fault: the relay waits for the subscriber without spending an attempt (§14 K11).
                throw OutboxTransportException.unavailable(failure.errorCode(), failure.getMessage(), failure);
            }
            throw new OutboxTransportException(failure.errorCode(), failure.getMessage(), failure.getCause(),
                failure.permanent());
        }
    }
}
