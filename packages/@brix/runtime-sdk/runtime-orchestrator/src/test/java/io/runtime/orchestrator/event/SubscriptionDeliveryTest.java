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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    void aSubscriberThatHasNotStartedIsWaitedForWithoutSpendingTheAttempt() {
        // Kafka design §14 K11: like the relay waiting for the broker (D21), nothing is parked or passed.
        world.pluginsStarted(false);

        SubscriptionDelivery.Outcome outcome = delivery.receive(event("1.0.0"), world.policy().maxAttempts());

        assertEquals(SubscriptionDelivery.Kind.WAIT, outcome.kind());
        assertEquals(SubscriptionDelivery.OWNER_UNAVAILABLE, outcome.errorCode());
    }

    @Test
    void anOwnerWhoseDatabaseIsUnavailableIsWaitedFor() {
        EventDeliveryException failure = new EventDeliveryException(EventDelivery.HANDLER_FAILED, "failed",
            new IllegalStateException("wrapped", new io.runtime.sdk.capability.PersistenceCapabilityException(
                io.runtime.sdk.capability.PersistenceCapabilityException.Code.UNAVAILABLE, java.util.Map.of(), null)),
            false);

        assertTrue(SubscriptionDelivery.unavailable(failure));
        assertTrue(!SubscriptionDelivery.unavailable(new EventDeliveryException(EventDelivery.HANDLER_FAILED, "failed",
            new IllegalStateException("the projection is failing"), false)), "a failing handler spends attempts");
    }

    @Test
    void anErrorFromAHandlerIsAFailedDeliveryNotADeadConsumer() {
        // Kafka design §14 K10: the consumer's thread survives a handler whose class cannot load.
        world.failNextWith(new NoClassDefFoundError("io/example/Missing"));

        SubscriptionDelivery.Outcome outcome = delivery.receive(event("1.0.0"), 1);

        assertEquals(SubscriptionDelivery.Kind.RETRY, outcome.kind());
    }

    @ParameterizedTest
    @MethodSource("handlerErrors")
    void everyErrorAHandlerThrowsFailsOnlyItsDelivery(Error error) {
        // Kafka design §14 K10: not only a class that cannot load; a failed assertion or a broken service file too.
        world.failNextWith(error);

        SubscriptionDelivery.Outcome outcome = delivery.receive(event("1.0.0"), 1);

        assertEquals(SubscriptionDelivery.Kind.RETRY, outcome.kind());
        assertEquals(SubscriptionDelivery.Outcome.DELIVERED, delivery.receive(event("1.0.0"), 2));
    }

    static Stream<Error> handlerErrors() {
        return Stream.of(new AssertionError("an invariant the handler checks"),
            new java.util.ServiceConfigurationError("a provider that cannot be instantiated"),
            new StackOverflowError());
    }

    @Test
    void theProcessRunningOutOfMemoryIsNotChargedToTheEvent() {
        // The JVM failing is not the handler's failure: it goes on to the consumer's loop, which starts again from the
        // committed position without spending an attempt.
        world.failNextWith(new OutOfMemoryError("Java heap space"));

        assertThrows(OutOfMemoryError.class, () -> delivery.receive(event("1.0.0"), 1));
    }

    @Test
    void aHandlerThatDoesNotFinishByItsDeadlineIsCancelledAndFails() {
        // Root BRX-RS-RESILIENCE-001: the deadline is enforced, not advisory.
        world.hanging(true);
        SubscriptionDelivery shortDeadline = new SubscriptionDelivery(subscription,
            world.newDelivery(Duration.ofMillis(200)), world.policy(), ignored -> Duration.ZERO, EventSubscriptionMetrics.NOOP);

        SubscriptionDelivery.Outcome outcome = shortDeadline.receive(event("1.0.0"), 1);

        assertEquals(SubscriptionDelivery.Kind.RETRY, outcome.kind());
        assertEquals(EventDelivery.HANDLER_FAILED, outcome.errorCode());
        assertEquals(List.of(), world.handled(), "its unit of work rolled back");
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
