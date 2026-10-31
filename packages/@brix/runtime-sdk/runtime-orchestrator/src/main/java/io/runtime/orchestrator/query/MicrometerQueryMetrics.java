/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * The query meters, when the host has Micrometer (query design §5.7). Tags are bounded: the calling plugin, the query
 * type and the outcome (root BRX-RS-OTEL-006).
 *
 * <p>The gauges read state this object keeps strongly, so they keep reporting however often the registry collects
 * (Micrometer holds a gauge's object weakly).</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class MicrometerQueryMetrics implements QueryMetrics {

    /** Calls by caller, query and outcome, with their durations. */
    public static final String CALLS = "brix.query.calls";
    /** 1 while the circuit of a caller and query is open. */
    public static final String CIRCUIT_OPEN = "brix.query.circuit.open";
    /** Calls of a query running now. */
    public static final String IN_FLIGHT = "brix.query.in_flight";

    private final Supplier<MeterRegistry> registry;
    private final Map<String, AtomicInteger> circuits = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();

    /**
     * Creates the meters.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     */
    public MicrometerQueryMetrics(Supplier<MeterRegistry> registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void called(String consumerPluginId, String queryType, String outcome, Duration duration) {
        MeterRegistry meters = registry.get();
        if (meters == null) {
            return;
        }
        Timer.builder(CALLS)
            .description("Typed query calls by caller, query and outcome")
            .tag("consumer", consumerPluginId)
            .tag("query", queryType)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(meters)
            .record(duration);
    }

    @Override
    public void circuit(String consumerPluginId, String queryType, boolean open) {
        MeterRegistry meters = registry.get();
        if (meters == null) {
            return;
        }
        circuits.computeIfAbsent(consumerPluginId + "|" + queryType, key -> {
            AtomicInteger state = new AtomicInteger();
            Gauge.builder(CIRCUIT_OPEN, state, AtomicInteger::get)
                .description("1 while the circuit breaker of a caller and query is open")
                .tag("consumer", consumerPluginId)
                .tag("query", queryType)
                .register(meters);
            return state;
        }).set(open ? 1 : 0);
    }

    @Override
    public void inFlight(String queryType, int delta) {
        MeterRegistry meters = registry.get();
        if (meters == null) {
            return;
        }
        inFlight.computeIfAbsent(queryType, key -> {
            AtomicInteger running = new AtomicInteger();
            Gauge.builder(IN_FLIGHT, running, AtomicInteger::get)
                .description("Calls of a typed query running now")
                .tag("query", queryType)
                .register(meters);
            return running;
        }).addAndGet(delta);
    }
}
