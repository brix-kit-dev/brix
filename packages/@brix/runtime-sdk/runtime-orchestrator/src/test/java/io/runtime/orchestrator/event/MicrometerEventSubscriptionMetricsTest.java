/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.ref.WeakReference;
import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * A subscription's consumer gauges read the consumer that watches now, for as long as the Host runs (Kafka design
 * §14 K10).
 */
class MicrometerEventSubscriptionMetricsTest {

    private static final String SUBSCRIPTION = "platform-audit.identity-audit-fact-record.v1";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventSubscriptionMetricsFactory factory = MicrometerEventSubscriptionMetrics.factory(() -> registry);

    @Test
    void theConsumerGaugesKeepReadingAfterACollection() throws InterruptedException {
        WatchedConsumer consumer = new WatchedConsumer(Duration.ofSeconds(7), true);
        // Bound method references, as the Kafka consumer passes them: only the metrics keep them.
        factory.forSubscription(SUBSCRIPTION).watch(consumer::handlingFor, consumer::running);

        collectGarbage();

        assertEquals(7, gauge(MicrometerEventSubscriptionMetrics.HANDLING_AGE));
        assertEquals(1, gauge(MicrometerEventSubscriptionMetrics.RUNNING));
    }

    @Test
    void aConsumerStartedAgainReportsThroughTheGaugesRegisteredFirst() {
        WatchedConsumer stopped = new WatchedConsumer(Duration.ZERO, false);
        WatchedConsumer started = new WatchedConsumer(Duration.ofSeconds(2), true);
        factory.forSubscription(SUBSCRIPTION).watch(stopped::handlingFor, stopped::running);

        factory.forSubscription(SUBSCRIPTION).watch(started::handlingFor, started::running);

        assertEquals(1, gauge(MicrometerEventSubscriptionMetrics.RUNNING));
        assertEquals(2, gauge(MicrometerEventSubscriptionMetrics.HANDLING_AGE));
        assertEquals(1, registry.find(MicrometerEventSubscriptionMetrics.RUNNING).gauges().size());
    }

    @Test
    void withoutAWatchingConsumerThereAreNoConsumerGauges() {
        factory.forSubscription(SUBSCRIPTION).recordLag(3);

        assertNull(registry.find(MicrometerEventSubscriptionMetrics.RUNNING).gauge());
        assertNull(registry.find(MicrometerEventSubscriptionMetrics.HANDLING_AGE).gauge());
        assertEquals(3, gauge(MicrometerEventSubscriptionMetrics.LAG));
    }

    private double gauge(String name) {
        return registry.get(name).tag("subscription", SUBSCRIPTION).gauge().value();
    }

    /** Runs the collector until it has cleared an object that only a weak reference reaches. */
    private static void collectGarbage() throws InterruptedException {
        WeakReference<Object> canary = new WeakReference<>(new Object());
        for (int round = 0; round < 50 && canary.get() != null; round++) {
            System.gc();
            Thread.sleep(10);
        }
        assumeTrue(canary.get() == null, "the collector did not run");
    }

    /** A consumer as the metrics see it from outside its thread. */
    private record WatchedConsumer(Duration handling, boolean consuming) {

        Duration handlingFor() {
            return handling;
        }

        boolean running() {
            return consuming;
        }
    }
}
