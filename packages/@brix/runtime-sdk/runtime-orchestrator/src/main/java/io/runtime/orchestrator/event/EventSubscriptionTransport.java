/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * L2B boundary to a broker that delivers events per subscription (Kafka design §4.2, §11.1).
 * <p>The runtime creates every subscription (root BRX-RS-MANIFEST-029): once the plugins have started it
 * asks the adapter to prepare the broker for the declared event types and subscriptions, then to consume
 * each subscription, handing every record to a {@link SubscriptionDelivery} in order per partition key.
 * The adapter keeps an event a subscription could not deliver in that subscription's parking place until
 * an operator replays it. Plugins never see a consumer, a topic or an offset.</p>
 */
public interface EventSubscriptionTransport {

    /**
     * Whether the broker can be reached now.
     *
     * @return reachability
     */
    boolean ready();

    /**
     * Makes sure the broker holds what the declared events need, such as a topic per event type and a
     * parking place per subscription.
     *
     * @param eventTypes every event type the plugins publish or subscribe to
     * @param subscriptions every declared subscription
     * @throws RuntimeException when the broker cannot be prepared now; the runtime tries again
     */
    void prepare(Set<String> eventTypes, List<ConsumedSubscription> subscriptions);

    /**
     * Starts consuming a subscription.
     *
     * @param subscription the subscription
     * @param delivery delivers each record and decides what happens to it
     * @return the running consumption
     */
    Consumption consume(ConsumedSubscription subscription, SubscriptionDelivery delivery);

    /**
     * Lists the parked events of each subscription, the earliest parked first, without payloads.
     *
     * @param limit most events listed per subscription
     * @return parked events by subscription name; a subscription with none may be left out
     */
    Map<String, List<ParkedEvent>> parked(int limit);

    /**
     * Finds a parked event of a subscription.
     *
     * @param subscription the subscription
     * @param eventId the event id, as listed
     * @return the parked event, or empty when the subscription holds none with this id
     */
    Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId);

    /**
     * Returns a parked event of a subscription as it was received, to deliver it again.
     *
     * @param subscription the subscription
     * @param eventId the event id, as listed
     * @return the event, or empty when none is parked with this id or the record is not an event envelope
     */
    Optional<CanonicalOutboxMessage> parkedMessage(ConsumedSubscription subscription, String eventId);

    /**
     * Releases a parked event once it was delivered again.
     *
     * @param subscription the subscription
     * @param eventId the event id, as listed
     */
    void release(ConsumedSubscription subscription, String eventId);

    /**
     * A subscription being consumed.
     */
    interface Consumption {

        /**
         * Whether the subscription is being consumed.
         *
         * @return whether it runs
         */
        boolean running();

        /**
         * How long the record in the handler has been there, zero when none is (Kafka design §14 K10).
         *
         * @return the handling age
         */
        default Duration handlingFor() {
            return Duration.ZERO;
        }

        /**
         * Stops fetching, lets the record in hand finish, keeps the position reached and closes.
         *
         * @param timeout how long to wait for the record in hand
         */
        void stop(Duration timeout);
    }
}
