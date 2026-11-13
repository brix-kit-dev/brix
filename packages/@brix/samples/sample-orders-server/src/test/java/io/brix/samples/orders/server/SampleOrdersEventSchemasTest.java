/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brix.samples.orders.api.SampleOrderActivityPayload;
import io.brix.samples.orders.api.SampleOrderEvents;
import io.brix.samples.orders.api.SampleOrderPlacedPayload;
import io.brix.samples.orders.api.SampleOrderQuantityAdjustedPayload;
import io.runtime.orchestrator.event.conformance.EventSchemaContract;

/**
 * The payloads sample-orders publishes match the schemas its manifest names (Kafka design D24): a payload that drifts
 * from its schema fails the build, not the subscribers.
 */
class SampleOrdersEventSchemasTest {

    @Test
    void everyPublishedEventMatchesItsSchema() {
        EventSchemaContract.verify(SampleOrdersPlugin.class, Map.of(
            SampleOrderEvents.PLACED, new SampleOrderPlacedPayload(42L, "1001", "sku-1", 2, 1L),
            SampleOrderEvents.QUANTITY_ADJUSTED, new SampleOrderQuantityAdjustedPayload(42L, "1001", "sku-1", 5, 2L),
            SampleOrderEvents.ACTIVITY, new SampleOrderActivityPayload(42L, "1001", "placed")));
    }

    @Test
    void aPayloadTheSchemaDoesNotDescribeFailsTheBuild() {
        record Renamed(long tenantId, String orderId, String product, int quantity, long version) {
        }

        assertThrows(AssertionError.class, () -> EventSchemaContract.verify(SampleOrdersPlugin.class, Map.of(
            SampleOrderEvents.PLACED, new Renamed(42L, "1001", "sku-1", 2, 1L),
            // Every other event is right, so the rename alone fails it.
            SampleOrderEvents.QUANTITY_ADJUSTED, new SampleOrderQuantityAdjustedPayload(42L, "1001", "sku-1", 5, 2L),
            SampleOrderEvents.ACTIVITY, new SampleOrderActivityPayload(42L, "1001", "placed"))));
    }
}
