/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.core;

/**
 * {@code SampleOrderQuantityAdjusted} as this subscriber reads it: the fields of the published schema
 * {@code brix://schemas/sample-orders/sample-order-quantity-adjusted/1.0.0}, the order as it is after an operator's
 * change. Like {@link OrderPlaced}, it is the plugin's own type, so the two plugins share only the schema.
 *
 * @param tenantId the tenant
 * @param orderId the order
 * @param sku what was ordered
 * @param quantity how many the order has now
 * @param version the order's version after the change, to tell whether it is newer than what the shipment shows
 *     (Kafka design D31)
 */
public record OrderQuantityAdjusted(long tenantId, String orderId, String sku, int quantity, long version) {
}
