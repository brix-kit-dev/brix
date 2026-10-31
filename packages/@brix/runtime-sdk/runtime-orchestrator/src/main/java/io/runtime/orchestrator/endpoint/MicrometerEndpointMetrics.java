/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

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
 * The dispatcher's meters (standard-link plan L7.5), labelled by {@code module} and {@code endpoint}:
 * <ul>
 *   <li>{@value #CALLS}: calls and how long they took, by {@code outcome};</li>
 *   <li>{@value #IN_FLIGHT}: calls whose handler is running now, which the host's per-endpoint limit caps.</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class MicrometerEndpointMetrics implements EndpointMetrics {

    /** Calls, by outcome. */
    public static final String CALLS = "brix.endpoint.calls";
    /** Calls whose handler is running. */
    public static final String IN_FLIGHT = "brix.endpoint.in_flight";

    private final Supplier<MeterRegistry> registry;
    private final Map<String, AtomicInteger> running = new ConcurrentHashMap<>();

    /**
     * Creates the meters.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     */
    public MicrometerEndpointMetrics(Supplier<MeterRegistry> registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void finished(String pluginId, String endpointId, String outcome, Duration took) {
        MeterRegistry meters = registry.get();
        if (meters != null) {
            Timer.builder(CALLS).description("Endpoint calls and how long the runtime took to answer them")
                .tag("module", pluginId).tag("endpoint", endpointId).tag("outcome", outcome)
                .register(meters).record(took);
        }
    }

    @Override
    public void inFlight(String pluginId, String endpointId, int delta) {
        running.computeIfAbsent(pluginId + "/" + endpointId, key -> {
            AtomicInteger count = new AtomicInteger();
            MeterRegistry meters = registry.get();
            if (meters != null) {
                Gauge.builder(IN_FLIGHT, count, AtomicInteger::doubleValue)
                    .description("Endpoint calls whose handler is running")
                    .tag("module", pluginId).tag("endpoint", endpointId).register(meters);
            }
            return count;
        }).addAndGet(delta);
    }
}
