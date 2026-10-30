/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.conformance.EventSchemaContract;

/**
 * The payloads sample-orders publishes match the schemas its manifest names (Kafka design D24): a payload that drifts
 * from its schema fails the build, not the subscribers.
 */
class SampleOrdersEventSchemasTest {

    @Test
    void everyPublishedEventMatchesItsSchema() {
        EventSchemaContract.verify(SampleOrdersPlugin.class, Map.of(
            SampleOrderPlaced.TYPE, new SampleOrderPlaced.Payload(42L, "1001", "sku-1", 2, 1L),
            SampleOrderActivity.TYPE, new SampleOrderActivity.Payload(42L, "1001", "placed")));
    }

    @Test
    void aPayloadTheSchemaDoesNotDescribeFailsTheBuild() {
        record Renamed(long tenantId, String orderId, String product, int quantity, long version) {
        }

        assertThrows(AssertionError.class, () -> EventSchemaContract.verify(SampleOrdersPlugin.class, Map.of(
            SampleOrderPlaced.TYPE, new Renamed(42L, "1001", "sku-1", 2, 1L),
            SampleOrderActivity.TYPE, new SampleOrderActivity.Payload(42L, "1001", "placed"))));
    }
}
