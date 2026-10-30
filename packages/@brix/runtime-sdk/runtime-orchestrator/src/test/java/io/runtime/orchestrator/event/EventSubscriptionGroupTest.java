/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.conformance.ConformanceWorld;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxRelayProperties;

/**
 * The runtime consumes the declared subscriptions only once the plugins have started and the broker answers
 * (root BRX-RS-A-5, MANIFEST-029; Kafka design §4.2).
 */
class EventSubscriptionGroupTest {

    private static final String SUBSCRIPTION = ConformanceWorld.SUBSCRIBER + "." + ConformanceWorld.HANDLER;

    private final ConformanceWorld world = new ConformanceWorld();
    private final RecordingBroker broker = new RecordingBroker();
    private final EventSubscriptionGroup group = new EventSubscriptionGroup(properties(), world.policy(), world::newDelivery,
        () -> broker, EventSubscriptionMetricsFactory.NOOP);

    @AfterEach
    void stopGroup() {
        group.stop();
    }

    @Test
    void nothingIsConsumedBeforeThePluginsStartAndTheBrokerAnswers() {
        world.pluginsStarted(false);
        broker.reachable = false;
        group.start();

        pause(Duration.ofMillis(300));
        assertEquals(List.of(), broker.calls, "the plugins have not started");
        world.pluginsStarted(true);
        pause(Duration.ofMillis(300));
        assertEquals(List.of(), broker.calls, "the broker does not answer yet");
        broker.reachable = true;

        await(group::isReady);
        // Kafka design §14 K22: the provided command is consumed like a subscription.
        String command = ConformanceWorld.SUBSCRIBER + ".command." + ConformanceWorld.COMMAND_HANDLER;
        String fanout = ConformanceWorld.SUBSCRIBER + "." + ConformanceWorld.SETUP_HANDLER;
        assertEquals(List.of("prepare [" + ConformanceWorld.SETUP_EVENT + ", " + ConformanceWorld.EVENT + "] ["
                + SUBSCRIPTION + ", " + fanout + ", " + command + "]",
            "consume " + SUBSCRIPTION, "consume " + fanout, "consume " + command), broker.calls);

        group.stop();
        assertTrue(broker.calls.contains("stop " + SUBSCRIPTION));
        assertTrue(broker.calls.contains("stop " + command));
        assertTrue(broker.calls.contains("stop " + fanout));
        assertFalse(group.isRunning());
    }

    @Test
    void aBrokerThatCannotBePreparedIsAskedAgain() {
        broker.refusals.set(1);

        group.start();

        await(group::isReady);
        assertEquals(2, broker.calls.stream().filter(call -> call.startsWith("prepare")).count());
        assertTrue(broker.calls.contains("consume " + SUBSCRIPTION));
    }

    private static OutboxRelayProperties properties() {
        OutboxRelayProperties properties = new OutboxRelayProperties();
        properties.setOwner("test");
        properties.setTransport("kafka");
        properties.setPollDelay(Duration.ofMillis(20));
        properties.setLeaseDuration(Duration.ofSeconds(2));
        return properties;
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out");
            }
            pause(Duration.ofMillis(20));
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
    }

    /** A broker that records what the group asks of it. */
    private static final class RecordingBroker implements EventSubscriptionTransport {

        private final List<String> calls = new CopyOnWriteArrayList<>();
        private final AtomicInteger refusals = new AtomicInteger();
        private volatile boolean reachable = true;

        @Override
        public boolean ready() {
            return reachable;
        }

        @Override
        public void prepare(Set<String> eventTypes, List<ConsumedSubscription> subscriptions) {
            calls.add("prepare " + eventTypes + " " + subscriptions.stream().map(ConsumedSubscription::name).toList());
            if (refusals.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                throw new IllegalStateException("the broker refuses for now");
            }
        }

        @Override
        public Consumption consume(ConsumedSubscription subscription, SubscriptionDelivery delivery) {
            calls.add("consume " + subscription.name());
            return new Consumption() {
                private volatile boolean running = true;

                @Override
                public boolean running() {
                    return running;
                }

                @Override
                public void stop(Duration timeout) {
                    running = false;
                    calls.add("stop " + subscription.name());
                }
            };
        }

        @Override
        public Map<String, List<ParkedEvent>> parked(int limit) {
            return Map.of();
        }

        @Override
        public Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId) {
            return Optional.empty();
        }

        @Override
        public Optional<CanonicalOutboxMessage> parkedMessage(ConsumedSubscription subscription, String eventId) {
            return Optional.empty();
        }

        @Override
        public void release(ConsumedSubscription subscription, String eventId) {
        }
    }
}
