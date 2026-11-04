/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.Objects;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Command delivery meters on Micrometer (standard-link plan point 8). The tags are the declared owner and handler,
 * so their number is bounded by the manifests.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class MicrometerCommandMetrics implements CommandMetrics {

    /** Commands that reused an idempotency key with other content, per owner and handler. */
    public static final String CONFLICTS = "brix.command.idempotency.conflicts";

    private final Supplier<MeterRegistry> registry;

    /**
     * Creates the meters.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     */
    public MicrometerCommandMetrics(Supplier<MeterRegistry> registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void conflict(String ownerId, String handlerId) {
        MeterRegistry meters = registry.get();
        if (meters != null) {
            Counter.builder(CONFLICTS).description("Commands that reused an idempotency key with other content")
                .tag("owner", ownerId).tag("handler", handlerId).register(meters).increment();
        }
    }
}
