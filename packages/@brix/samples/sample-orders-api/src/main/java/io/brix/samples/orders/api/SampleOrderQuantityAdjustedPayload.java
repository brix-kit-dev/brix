/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The payload of {@code SampleOrderQuantityAdjusted}, as
 * {@code brix://schemas/sample-orders/sample-order-quantity-adjusted/1.0.0} describes it. It carries the order as it is
 * after the change, not the difference: a subscriber that sees it again, or after a later change, tells from the
 * version which state is newer.
 *
 * @param tenantId the tenant
 * @param orderId the order
 * @param sku what was ordered
 * @param quantity how many the order has now
 * @param version the order's version after the change, for subscribers that order events of several types (Kafka
 *     design D31)
 */
public record SampleOrderQuantityAdjustedPayload(long tenantId, String orderId, String sku, int quantity,
        long version) {
}
