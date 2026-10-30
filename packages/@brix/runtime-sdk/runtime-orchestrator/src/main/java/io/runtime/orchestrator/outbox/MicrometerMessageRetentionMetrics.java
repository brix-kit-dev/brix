/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Meters of the messaging tables' cleaning (Kafka design §14 K16).
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class MicrometerMessageRetentionMetrics implements MessageRetentionMetrics {

    /** Rows deleted, per table. */
    public static final String DELETED = "brix.messaging.retention.deleted";
    /** Tables a pass could not clean, per table. */
    public static final String FAILURES = "brix.messaging.retention.failures";
    /** When the last pass ended, in seconds since the epoch; 0 before the first. */
    public static final String LAST_PASS = "brix.messaging.retention.last.pass";

    private final Supplier<MeterRegistry> registry;
    private final AtomicLong lastPass = new AtomicLong();
    private volatile boolean registered;

    /**
     * Creates the meters.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     */
    public MicrometerMessageRetentionMetrics(Supplier<MeterRegistry> registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void deleted(String table, int rows) {
        MeterRegistry meters = registry.get();
        if (meters != null && rows > 0) {
            Counter.builder(DELETED).description("Expired messaging records deleted").tag("table", table)
                .register(meters).increment(rows);
        }
    }

    @Override
    public void failed(String table) {
        MeterRegistry meters = registry.get();
        if (meters != null) {
            Counter.builder(FAILURES).description("Messaging tables a cleaning pass could not clean")
                .tag("table", table).register(meters).increment();
        }
    }

    @Override
    public void passed(Instant at) {
        lastPass.set(at.getEpochSecond());
        MeterRegistry meters = registry.get();
        if (meters != null && !registered) {
            Gauge.builder(LAST_PASS, lastPass, AtomicLong::get)
                .description("When the last cleaning pass of the messaging tables ended")
                .baseUnit("seconds")
                .register(meters);
            registered = true;
        }
    }
}
