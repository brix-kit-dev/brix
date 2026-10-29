/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.conformance.ConformanceWorld;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Each record of a subscription is delivered, retried in place with the relay's backoff, or parked
 * (Kafka design §4.2, D20).
 */
class SubscriptionDeliveryTest {

    private final ConformanceWorld world = new ConformanceWorld();
    private final List<String> recorded = new ArrayList<>();
    private final ConsumedSubscription subscription =
        new ConsumedSubscription(ConformanceWorld.SUBSCRIBER, ConformanceWorld.HANDLER, ConformanceWorld.EVENT);
    private final SubscriptionDelivery delivery = new SubscriptionDelivery(subscription, world.newDelivery(),
        world.policy(), new EventSubscriptionMetrics() {
            @Override
            public void recordDelivered(String eventType) {
                recorded.add("delivered " + eventType);
            }

            @Override
            public void recordRetry(String eventType, String errorCode) {
                recorded.add("retry " + errorCode);
            }

            @Override
            public void recordParked(String eventType, String errorCode) {
                recorded.add("parked " + errorCode);
            }
        });

    @Test
    void aDeliveredRecordIsDoneAndTheHandlerSeesTheConsumersAttempt() {
        SubscriptionDelivery.Outcome outcome = delivery.receive(event("1.0.0"), 2);

        assertEquals(SubscriptionDelivery.Outcome.DELIVERED, outcome);
        assertEquals(2, world.handled().get(0).attempt());
        assertEquals(List.of("delivered " + ConformanceWorld.EVENT), recorded);
    }

    @Test
    void aFailureIsRetriedAfterABackoffThatGrowsWithTheAttempt() {
        world.failing(true);

        SubscriptionDelivery.Outcome first = delivery.receive(event("1.0.0"), 1);
        SubscriptionDelivery.Outcome second = delivery.receive(event("1.0.0"), 2);

        assertEquals(SubscriptionDelivery.Kind.RETRY, first.kind());
        assertEquals(EventDelivery.HANDLER_FAILED, first.errorCode());
        assertEquals(Duration.ofMillis(100), first.delay());
        assertEquals(Duration.ofMillis(200), second.delay());
        assertEquals(List.of("retry " + EventDelivery.HANDLER_FAILED, "retry " + EventDelivery.HANDLER_FAILED), recorded);
    }

    @Test
    void theLastAttemptParks() {
        world.failing(true);

        SubscriptionDelivery.Outcome last = delivery.receive(event("1.0.0"), world.policy().maxAttempts());

        assertEquals(SubscriptionDelivery.Kind.PARK, last.kind());
        assertEquals(EventDelivery.HANDLER_FAILED, last.errorCode());
    }

    @Test
    void aPermanentFailureParksOnTheFirstAttempt() {
        SubscriptionDelivery.Outcome outcome = delivery.receive(event("2.0.0"), 1);

        assertEquals(SubscriptionDelivery.Kind.PARK, outcome.kind());
        assertEquals(EventDelivery.SCHEMA_UNSUPPORTED, outcome.errorCode());
        assertEquals(0, world.calls());
    }

    @Test
    void anEventForASubscriptionItsTypeDoesNotHaveIsRefusedForGood() {
        EventDeliveryException refused = assertThrows(EventDeliveryException.class,
            () -> world.newDelivery().deliverTo(ConformanceWorld.SUBSCRIBER, "removed-handler.v1", event("1.0.0")));

        assertEquals(EventDelivery.SUBSCRIPTION_UNKNOWN, refused.errorCode());
        assertTrue(refused.permanent());
    }

    private CanonicalOutboxMessage event(String schemaVersion) {
        return world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1, schemaVersion);
    }
}
