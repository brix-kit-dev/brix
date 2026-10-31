/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Query design §5.7 (root BRX-RS-OTEL-006): calls, open circuits and calls in flight, tagged by caller, query and
 * outcome only.
 */
class MicrometerQueryMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerQueryMetrics metrics = new MicrometerQueryMetrics(() -> registry);

    @Test
    void callsAreCountedByCallerQueryAndOutcome() {
        metrics.called("sample-orders", "sample-shipping.find-shipment", QueryMetrics.OK, Duration.ofMillis(12));
        metrics.called("sample-orders", "sample-shipping.find-shipment", "query.deadline_exceeded", Duration.ofSeconds(2));
        metrics.called("sample-orders", "sample-shipping.find-shipment", QueryMetrics.OK, Duration.ofMillis(8));

        assertEquals(2, registry.get(MicrometerQueryMetrics.CALLS).tag("outcome", "ok").timer().count());
        assertEquals(1, registry.get(MicrometerQueryMetrics.CALLS).tag("outcome", "query.deadline_exceeded").timer()
            .count());
        assertNull(registry.find(MicrometerQueryMetrics.CALLS).tag("tenant", "100").timer(), "no tenant tag");
    }

    @Test
    void theGaugesKeepReportingTheirState() {
        metrics.circuit("sample-orders", "sample-shipping.find-shipment", true);
        metrics.inFlight("sample-shipping.find-shipment", 1);
        metrics.inFlight("sample-shipping.find-shipment", 1);
        metrics.inFlight("sample-shipping.find-shipment", -1);
        System.gc();

        assertEquals(1.0, registry.get(MicrometerQueryMetrics.CIRCUIT_OPEN).gauge().value());
        assertEquals(1.0, registry.get(MicrometerQueryMetrics.IN_FLIGHT).gauge().value());
        metrics.circuit("sample-orders", "sample-shipping.find-shipment", false);
        assertEquals(0.0, registry.get(MicrometerQueryMetrics.CIRCUIT_OPEN).gauge().value());
    }

    @Test
    void withoutARegistryNothingIsRecorded() {
        MicrometerQueryMetrics none = new MicrometerQueryMetrics(() -> null);

        none.called("a", "b.c", QueryMetrics.OK, Duration.ZERO);
        none.circuit("a", "b.c", true);
        none.inFlight("b.c", 1);
    }
}
