/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Conflicts of reused keys are counted per owner and handler (standard-link plan point 8).
 */
class MicrometerCommandMetricsTest {

    @Test
    void conflictsAreCountedPerOwnerAndHandler() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerCommandMetrics metrics = new MicrometerCommandMetrics(() -> registry);

        metrics.conflict("sample-orders", "sample-orders.cancel-order.v1");
        metrics.conflict("sample-orders", "sample-orders.cancel-order.v1");
        metrics.conflict("app-stock", "reserve-stock.v1");

        assertEquals(2.0, registry.get(MicrometerCommandMetrics.CONFLICTS).tag("owner", "sample-orders")
            .tag("handler", "sample-orders.cancel-order.v1").counter().count());
        assertEquals(1.0, registry.get(MicrometerCommandMetrics.CONFLICTS).tag("owner", "app-stock").counter().count());
    }

    @Test
    void withoutARegistryNothingIsRecorded() {
        new MicrometerCommandMetrics(() -> null).conflict("sample-orders", "sample-orders.cancel-order.v1");
    }
}
