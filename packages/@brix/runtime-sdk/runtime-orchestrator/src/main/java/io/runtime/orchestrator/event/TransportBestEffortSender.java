/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.util.Objects;
import java.util.function.Supplier;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransport;

/**
 * Sends a BEST_EFFORT event through the transport the relay uses for the outbox's events: in process it is
 * delivered to its subscribers at once, with Kafka it goes to its event type's topic and the same consumers
 * deliver it (Kafka design §14 K21). A failed send is logged and the event dropped (root BRX-RS-MESSAGE-019).
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class TransportBestEffortSender implements BestEffortEventSender {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransportBestEffortSender.class);

    private final Supplier<OutboxTransport> transport;

    /**
     * Creates the sender.
     *
     * @param transport the relay's transport, when there is one
     */
    public TransportBestEffortSender(Supplier<OutboxTransport> transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public void send(CanonicalOutboxMessage message) {
        OutboxTransport current = transport.get();
        if (current == null) {
            log.warn("event=event.best_effort_dropped type={} event_id={} reason=no_transport", message.messageType(),
                message.eventId());
            return;
        }
        try {
            current.publish(message);
        } catch (RuntimeException dropped) {
            log.warn("event=event.best_effort_dropped type={} event_id={}", message.messageType(), message.eventId(),
                dropped);
        }
    }
}
