/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * The external call meters (standard-link plan L8.4): {@value #CALLS}, calls and how long they took, by
 * {@code module}, {@code service} and {@code outcome}.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class MicrometerExternalHttpMetrics implements ExternalHttpMetrics {

    /** Calls, by outcome. */
    public static final String CALLS = "brix.external_http.calls";

    private final Supplier<MeterRegistry> registry;

    /**
     * Creates the meters.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     */
    public MicrometerExternalHttpMetrics(Supplier<MeterRegistry> registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void finished(String pluginId, String serviceId, String outcome, Duration took) {
        MeterRegistry meters = registry.get();
        if (meters != null) {
            Timer.builder(CALLS).description("Calls to external services and how long they took, retries included")
                .tag("module", pluginId).tag("service", serviceId).tag("outcome", outcome)
                .register(meters).record(took);
        }
    }
}
