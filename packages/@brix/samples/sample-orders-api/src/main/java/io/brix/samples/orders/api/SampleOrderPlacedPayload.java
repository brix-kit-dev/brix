/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The payload of {@code SampleOrderPlaced}, as {@code brix://schemas/sample-orders/sample-order-placed/1.0.0}
 * describes it.
 *
 * @param tenantId the tenant
 * @param orderId the order
 * @param sku what was ordered
 * @param quantity how many
 * @param version the order's version, for subscribers that order events of several types (Kafka design D31)
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public record SampleOrderPlacedPayload(long tenantId, String orderId, String sku, int quantity, long version) {
}
