/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer meters of one subscription a broker consumes for, tagged with the subscription's name
 * (Kafka design §4.5). Tags stay bounded: subscription, event type and error code, never an event id
 * or a tenant (root BRX-RS-OTEL-006).
 *
 * <p>{@value #PARKED_NOW} counts the events the subscription holds parked; one stays counted until
 * someone replays it, so an alert on it does not clear by itself.</p>
 */
public final class MicrometerEventSubscriptionMetrics implements EventSubscriptionMetrics {

    /** Events delivered and committed by the subscriber. */
    public static final String DELIVERED = "brix.event.delivered";
    /** Deliveries that failed and wait for a retry. */
    public static final String RETRIES = "brix.event.retries";
    /** Events parked after a permanent failure or their last attempt. */
    public static final String PARKED = "brix.event.parked";
    /** Events the subscription holds parked now. */
    public static final String PARKED_NOW = "brix.event.subscription.parked";
    /** Records waiting behind the consumer's position. */
    public static final String LAG = "brix.event.subscription.lag";
    /** How long the record at the head of a partition has been failing, in seconds. */
    public static final String STALLED_AGE = "brix.event.subscription.stalled.age";

    private final MeterRegistry registry;
    private final String subscription;
    private final AtomicLong parkedNow = new AtomicLong();
    private final AtomicLong lag = new AtomicLong();
    private final AtomicLong stalledSeconds = new AtomicLong();

    /**
     * Creates one set of meters per subscription and keeps it, so a consumer that restarts reports through
     * the gauges it registered first.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     * @return the factory
     */
    public static EventSubscriptionMetricsFactory factory(Supplier<MeterRegistry> registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        Map<String, EventSubscriptionMetrics> subscriptions = new ConcurrentHashMap<>();
        return subscription -> {
            MeterRegistry meters = registry.get();
            return meters == null
                ? NOOP
                : subscriptions.computeIfAbsent(subscription, name -> new MicrometerEventSubscriptionMetrics(meters, name));
        };
    }

    /**
     * Registers the subscription's gauges.
     *
     * @param registry meter registry
     * @param subscription the subscription's name
     */
    public MicrometerEventSubscriptionMetrics(MeterRegistry registry, String subscription) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.subscription = Objects.requireNonNull(subscription, "subscription must not be null");
        Gauge.builder(PARKED_NOW, parkedNow, AtomicLong::get)
            .description("Events the subscription holds parked")
            .tag("subscription", subscription)
            .register(registry);
        Gauge.builder(LAG, lag, AtomicLong::get)
            .description("Records waiting behind the subscription's consumer position")
            .tag("subscription", subscription)
            .register(registry);
        Gauge.builder(STALLED_AGE, stalledSeconds, AtomicLong::get)
            .description("How long the record at the head of a partition has been failing")
            .baseUnit("seconds")
            .tag("subscription", subscription)
            .register(registry);
    }

    @Override
    public void recordDelivered(String eventType) {
        counter(DELIVERED, "Events delivered and committed by the subscriber", eventType, null).increment();
    }

    @Override
    public void recordRetry(String eventType, String errorCode) {
        counter(RETRIES, "Event deliveries that failed and wait for a retry", eventType, errorCode).increment();
    }

    @Override
    public void recordParked(String eventType, String errorCode) {
        counter(PARKED, "Events parked after a permanent failure or their last attempt", eventType, errorCode)
            .increment();
    }

    @Override
    public void recordParkedNow(int count) {
        parkedNow.set(count);
    }

    @Override
    public void recordLag(long records) {
        lag.set(records);
    }

    @Override
    public void recordStalled(Duration age) {
        stalledSeconds.set(age.toSeconds());
    }

    private Counter counter(String name, String description, String eventType, String errorCode) {
        Counter.Builder counter = Counter.builder(name)
            .description(description)
            .tag("subscription", subscription)
            .tag("type", eventType == null ? "unknown" : eventType);
        if (errorCode != null) {
            counter.tag("code", errorCode);
        }
        return counter.register(registry);
    }
}
