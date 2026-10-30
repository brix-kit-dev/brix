/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Lists the events each subscription parked and replays one by its event id (Kafka design §4.2, D20).
 *
 * <p>{@code GET /actuator/events} lists them without payloads; {@code POST /actuator/events/{eventId}}
 * delivers the event again, through the delivery core, to each subscription that parked it, and releases it
 * from a subscription once its subscriber committed. The event id stays the same, so a subscriber that had
 * already committed the event skips it through its inbox (root BRX-RS-MESSAGE-028). A replay answers 404
 * when no subscription holds the event parked, and 409 when a subscription could not take it again. The
 * endpoint is served on the management port, which the host does not publish; a host allows replays with
 * {@code management.endpoint.events.access=unrestricted}.</p>
 */
@WebEndpoint(id = "events", defaultAccess = Access.READ_ONLY)
public final class EventsEndpoint {

    /** Parked events listed per subscription. */
    static final int LIST_LIMIT = 100;
    /** A subscription could not take the event again. */
    static final int STATUS_CONFLICT = 409;

    private static final Logger log = LoggerFactory.getLogger(EventsEndpoint.class);

    private final Supplier<EventSubscriptionTransport> brokers;
    private final Supplier<EventDelivery> deliveries;

    /**
     * Creates the endpoint.
     *
     * @param brokers supplies the adapter that consumes from the broker; may supply {@code null}
     * @param deliveries supplies the delivery core; may supply {@code null}
     */
    public EventsEndpoint(Supplier<EventSubscriptionTransport> brokers, Supplier<EventDelivery> deliveries) {
        this.brokers = Objects.requireNonNull(brokers, "brokers must not be null");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries must not be null");
    }

    /**
     * Lists the parked events of each subscription, the earliest parked first.
     *
     * @return parked events by subscription
     */
    @ReadOperation
    public ParkedEvents parked() {
        EventSubscriptionTransport broker = brokers.get();
        return new ParkedEvents(broker == null ? Map.of() : new TreeMap<>(broker.parked(LIST_LIMIT)));
    }

    /**
     * Delivers the parked event with this id again to each subscription that parked it.
     *
     * @param eventId the event id, as listed
     * @return the result per subscription; 404 when none holds it parked, 409 when one could not take it
     */
    @WriteOperation
    public WebEndpointResponse<Replay> replay(@Selector String eventId) {
        EventSubscriptionTransport broker = brokers.get();
        EventDelivery delivery = deliveries.get();
        List<Replayed> results = new ArrayList<>();
        if (broker != null && delivery != null) {
            for (ConsumedSubscription subscription : consumed(delivery)) {
                if (broker.parkedEvent(subscription, eventId).isPresent()) {
                    results.add(replay(broker, delivery, subscription, eventId));
                }
            }
        }
        Replay replay = new Replay(eventId, List.copyOf(results));
        if (results.isEmpty()) {
            return new WebEndpointResponse<>(replay, WebEndpointResponse.STATUS_NOT_FOUND);
        }
        boolean all = results.stream().allMatch(Replayed::replayed);
        return new WebEndpointResponse<>(replay, all ? WebEndpointResponse.STATUS_OK : STATUS_CONFLICT);
    }

    /** The event subscriptions and the consumers of the provided commands (Kafka design §14 K22). */
    static List<ConsumedSubscription> consumed(EventDelivery delivery) {
        List<ConsumedSubscription> consumed = new ArrayList<>();
        for (EventSubscribers.DeclaredSubscriber declared : delivery.subscriptions()) {
            consumed.add(new ConsumedSubscription(declared.pluginId(), declared.subscription().handlerId(),
                declared.subscription().eventType()));
        }
        consumed.addAll(delivery.commandProvisions());
        return consumed;
    }

    private static Replayed replay(
            EventSubscriptionTransport broker, EventDelivery delivery, ConsumedSubscription subscription, String eventId) {
        Optional<CanonicalOutboxMessage> message = broker.parkedMessage(subscription, eventId);
        if (message.isEmpty()) {
            log.warn("event=event.replay_failed subscription={} event_id={} code={}", subscription.name(), eventId,
                SubscriptionDelivery.ENVELOPE_INVALID);
            return new Replayed(subscription.name(), false, SubscriptionDelivery.ENVELOPE_INVALID);
        }
        try {
            delivery.deliverTo(subscription.pluginId(), subscription.handlerId(), message.get());
        } catch (EventDeliveryException failure) {
            log.warn("event=event.replay_failed subscription={} event_id={} code={}", subscription.name(), eventId,
                failure.errorCode(), failure);
            return new Replayed(subscription.name(), false, failure.errorCode());
        }
        broker.release(subscription, eventId);
        log.info("event=event.replayed subscription={} type={} event_id={}", subscription.name(),
            message.get().messageType(), eventId);
        return new Replayed(subscription.name(), true, null);
    }

    /**
     * Parked events by subscription.
     *
     * @param subscriptions each subscription's parked events
     */
    public record ParkedEvents(Map<String, List<ParkedEvent>> subscriptions) {
    }

    /**
     * Result of a replay.
     *
     * @param eventId the requested event id
     * @param subscriptions the result in each subscription that held the event parked
     */
    public record Replay(String eventId, List<Replayed> subscriptions) {
    }

    /**
     * Result of a replay in one subscription.
     *
     * @param subscription the subscription's name
     * @param replayed whether the subscriber committed the event and the subscription released it
     * @param errorCode why it was not, or {@code null}
     */
    public record Replayed(String subscription, boolean replayed, String errorCode) {
    }
}
