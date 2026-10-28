/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer meters of the relay that serves one owner outbox, tagged with the outbox table.
 *
 * <p>{@value #MESSAGES} holds the rows by status as the relay read them on its last pass; a parked
 * row stays counted until someone replays it, so an alert on it does not clear by itself.</p>
 */
public final class MicrometerOutboxRelayMetrics implements OutboxRelayMetrics {

    /** Rows of the outbox by status: pending, in_flight, parked. */
    public static final String MESSAGES = "brix.outbox.messages";
    /** Age of the oldest pending row, in seconds. */
    public static final String OLDEST_PENDING_AGE = "brix.outbox.oldest.pending.age";
    /** Messages delivered and marked published. */
    public static final String PUBLISHED = "brix.outbox.published";
    /** Deliveries that failed and wait for a retry. */
    public static final String RETRIES = "brix.outbox.retries";
    /** Messages the relay parked. */
    public static final String PARKED = "brix.outbox.parked";

    private final MeterRegistry registry;
    private final String outbox;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong inFlight = new AtomicLong();
    private final AtomicLong parked = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    /**
     * Creates one set of meters per outbox and keeps it, so a relay that restarts reports through the
     * gauges it registered first.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     * @return the factory
     */
    public static OutboxRelayMetricsFactory factory(Supplier<MeterRegistry> registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        Map<String, OutboxRelayMetrics> outboxes = new ConcurrentHashMap<>();
        return outbox -> {
            MeterRegistry meters = registry.get();
            return meters == null
                ? NOOP
                : outboxes.computeIfAbsent(outbox, table -> new MicrometerOutboxRelayMetrics(meters, table));
        };
    }

    /**
     * Registers the outbox's gauges.
     *
     * @param registry meter registry
     * @param outbox the owner's outbox table
     */
    public MicrometerOutboxRelayMetrics(MeterRegistry registry, String outbox) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        rows("pending", pending);
        rows("in_flight", inFlight);
        rows("parked", parked);
        Gauge.builder(OLDEST_PENDING_AGE, oldestPendingAgeSeconds, AtomicLong::get)
            .description("Age of the oldest message waiting in the outbox")
            .baseUnit("seconds")
            .tag("outbox", outbox)
            .register(registry);
    }

    @Override
    public void recordBacklog(OutboxBacklogSnapshot snapshot) {
        pending.set(snapshot.pendingCount());
        inFlight.set(snapshot.inFlightCount());
        parked.set(snapshot.parkedCount());
        oldestPendingAgeSeconds.set(snapshot.oldestPendingAge().toSeconds());
    }

    @Override
    public void recordPublished(String messageType) {
        counter(PUBLISHED, "Outbox messages delivered and marked published", messageType, null).increment();
    }

    @Override
    public void recordRetry(String messageType, String errorCode) {
        counter(RETRIES, "Outbox deliveries that failed and wait for a retry", messageType, errorCode).increment();
    }

    @Override
    public void recordParked(String messageType, String errorCode) {
        counter(PARKED, "Outbox messages parked after a permanent failure or their last attempt", messageType, errorCode)
            .increment();
    }

    private void rows(String status, AtomicLong count) {
        Gauge.builder(MESSAGES, count, AtomicLong::get)
            .description("Outbox rows by status, as the relay read them on its last pass")
            .tag("outbox", outbox)
            .tag("status", status)
            .register(registry);
    }

    private Counter counter(String name, String description, String messageType, String errorCode) {
        Counter.Builder counter = Counter.builder(name)
            .description(description)
            .tag("outbox", outbox)
            .tag("type", messageType);
        if (errorCode != null) {
            counter.tag("code", errorCode);
        }
        return counter.register(registry);
    }
}
