/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.runtime.orchestrator.event.conformance.ConformanceWorld;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.sdk.capability.PersistenceCapabilityException;

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
    void theWaitForASubscriberGrowsWithEachTimeAndCarriesJitter() {
        // Root BRX-RS-RESILIENCE-002: a wait costs no attempt, so its tries need the backoff and the jitter themselves.
        world.pluginsStarted(false);
        SubscriptionDelivery jittered = new SubscriptionDelivery(subscription, world.newDelivery(), world.policy(),
            times -> Duration.ofMillis(times), EventSubscriptionMetrics.NOOP);

        assertEquals(Duration.ofMillis(1001), jittered.receive(event("1.0.0"), 1).delay());
        assertEquals(Duration.ofMillis(2002), jittered.receive(event("1.0.0"), 1, waited(1, Duration.ofSeconds(1))).delay());
        assertEquals(Duration.ofMillis(4003), jittered.receive(event("1.0.0"), 1, waited(2, Duration.ofSeconds(3))).delay());
        assertEquals(Duration.ofMillis(60_100), jittered.receive(event("1.0.0"), 1, waited(99, Duration.ofHours(1))).delay(),
            "it stays within a minute, so that the record leaves soon after the subscriber is back");
    }

    @Test
    void aRecordThatHasWaitedItsLimitIsParkedWithWhatItWaitedFor() {
        // Root BRX-RS-RESILIENCE-002, MESSAGE-028 (standard-link plan D91).
        world.pluginsStarted(false);
        Duration limit = world.policy().maxWait();

        assertEquals(SubscriptionDelivery.Kind.WAIT,
            delivery.receive(event("1.0.0"), 1, waited(3, limit.minusMillis(1))).kind());

        SubscriptionDelivery.Outcome parked = delivery.receive(event("1.0.0"), 1, waited(4, limit));

        assertEquals(SubscriptionDelivery.Kind.PARK, parked.kind());
        assertEquals(EventDelivery.SUBSCRIBER_NOT_READY, parked.errorCode());
    }

    @Test
    void aRecordThatWaitedAndThenWasDeliveredIsDone() {
        assertEquals(SubscriptionDelivery.Outcome.DELIVERED,
            delivery.receive(event("1.0.0"), 1, waited(4, world.policy().maxWait().plusHours(1))));
    }

    @Test
    void theLimitOfAWaitDoesNotShortenTheAttemptsOfAFailingHandler() {
        world.failing(true);

        assertEquals(SubscriptionDelivery.Kind.RETRY,
            delivery.receive(event("1.0.0"), 1, waited(4, world.policy().maxWait().plusHours(1))).kind());
    }

    @Test
    void aDatabaseThatCannotBeReachedIsWaitedFor() {
        assertTrue(waitsFor(unreachable(ConformanceWorld.SUBSCRIBER)));
        assertTrue(waitsFor(new IllegalStateException("wrapped by the handler", unreachable(ConformanceWorld.SUBSCRIBER))));
        assertTrue(waitsFor(unreachable("another-owner")), "all owners share one database");
    }

    @Test
    void whatElseAHandlerCannotReachSpendsAttempts() {
        // Standard-link plan D90: only the persistence adapter says that the database is gone. Nothing says that anything
        // else comes back, so waiting for it could hold the partition back for good.
        assertFalse(waitsFor(new IllegalStateException("the projection is failing")), "a failing handler");
        assertFalse(waitsFor(new IllegalStateException("the payment service", new ConnectException("Connection refused"))),
            "a service the handler calls");
        assertFalse(waitsFor(new IllegalStateException("a pool of its own", new SQLTransientConnectionException("timed out"))),
            "a connection the persistence adapter knows nothing about");
        assertFalse(waitsFor(new PersistenceCapabilityException(PersistenceCapabilityException.Code.FAILURE,
            Map.of("owner", ConformanceWorld.SUBSCRIBER))), "a failed statement of its own");
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

    private static SubscriptionDelivery.Waited waited(int times, Duration sofar) {
        return new SubscriptionDelivery.Waited(times, sofar);
    }

    private static boolean waitsFor(RuntimeException failure) {
        return SubscriptionDelivery.unavailable(EventDeliveryException.whileHandling(EventDelivery.HANDLER_FAILED, "failed",
            failure));
    }

    private static PersistenceCapabilityException unreachable(String owner) {
        return new PersistenceCapabilityException(PersistenceCapabilityException.Code.UNAVAILABLE, Map.of("owner", owner));
    }

    private CanonicalOutboxMessage event(String schemaVersion) {
        return world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1, schemaVersion);
    }
}
