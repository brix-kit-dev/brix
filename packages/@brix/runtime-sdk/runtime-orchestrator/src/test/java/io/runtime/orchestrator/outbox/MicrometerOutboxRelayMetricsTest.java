/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * The relay's meters are kept per outbox table; a parked row stays in the gauge until it is replayed.
 */
class MicrometerOutboxRelayMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void theBacklogOfEachOutboxIsAGaugeByStatus() {
        OutboxRelayMetricsFactory factory = MicrometerOutboxRelayMetrics.factory(() -> registry);

        factory.forOutbox("platform_tenant_outbox")
            .recordBacklog(new OutboxBacklogSnapshot(3, 1, 2, Duration.ofSeconds(90)));
        factory.forOutbox("platform_identity_outbox")
            .recordBacklog(new OutboxBacklogSnapshot(0, 0, 0, Duration.ZERO));

        assertEquals(3, rows("platform_tenant_outbox", "pending"));
        assertEquals(1, rows("platform_tenant_outbox", "in_flight"));
        assertEquals(2, rows("platform_tenant_outbox", "parked"));
        assertEquals(0, rows("platform_identity_outbox", "parked"));
        assertEquals(90, registry.get(MicrometerOutboxRelayMetrics.OLDEST_PENDING_AGE)
            .tag("outbox", "platform_tenant_outbox").gauge().value());
    }

    @Test
    void deliveriesAreCountedByTypeAndErrorCode() {
        OutboxRelayMetrics metrics = MicrometerOutboxRelayMetrics.factory(() -> registry).forOutbox("platform_tenant_outbox");

        metrics.recordPublished("TenantFirstOwnerAccepted");
        metrics.recordRetry("TenantFirstOwnerAccepted", "EVENT_HANDLER_FAILED");
        metrics.recordWait("TenantFirstOwnerAccepted", "KAFKA_ACCESS_DENIED");
        metrics.recordParked("TenantFirstOwnerAccepted", "EVENT_PAYLOAD_INVALID");
        metrics.recordParked("TenantFirstOwnerAccepted", "EVENT_PAYLOAD_INVALID");

        assertEquals(1, registry.get(MicrometerOutboxRelayMetrics.PUBLISHED)
            .tags("outbox", "platform_tenant_outbox", "type", "TenantFirstOwnerAccepted").counter().count());
        assertEquals(1, registry.get(MicrometerOutboxRelayMetrics.RETRIES)
            .tags("outbox", "platform_tenant_outbox", "code", "EVENT_HANDLER_FAILED").counter().count());
        assertEquals(1, registry.get(MicrometerOutboxRelayMetrics.WAITS)
            .tags("outbox", "platform_tenant_outbox", "type", "TenantFirstOwnerAccepted", "code", "KAFKA_ACCESS_DENIED")
            .counter().count());
        assertEquals(2, registry.get(MicrometerOutboxRelayMetrics.PARKED)
            .tags("outbox", "platform_tenant_outbox", "code", "EVENT_PAYLOAD_INVALID").counter().count());
    }

    @Test
    void aRestartedRelayReportsThroughTheGaugesItRegisteredFirst() {
        OutboxRelayMetricsFactory factory = MicrometerOutboxRelayMetrics.factory(() -> registry);
        OutboxRelayMetrics first = factory.forOutbox("platform_tenant_outbox");

        OutboxRelayMetrics again = factory.forOutbox("platform_tenant_outbox");
        again.recordBacklog(new OutboxBacklogSnapshot(0, 0, 4, Duration.ZERO));

        assertSame(first, again);
        assertEquals(4, rows("platform_tenant_outbox", "parked"));
    }

    @Test
    void withoutARegistryNothingIsRecorded() {
        assertSame(OutboxRelayMetrics.NOOP, MicrometerOutboxRelayMetrics.factory(() -> null).forOutbox("platform_tenant_outbox"));
    }

    private double rows(String outbox, String status) {
        return registry.get(MicrometerOutboxRelayMetrics.MESSAGES).tags("outbox", outbox, "status", status).gauge().value();
    }
}
