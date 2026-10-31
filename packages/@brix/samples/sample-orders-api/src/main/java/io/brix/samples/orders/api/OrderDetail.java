/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The answer of {@code GET /api/sample-orders/orders/{orderId}}: an order and how far its shipment is, as schema
 * {@code brix://schemas/sample-orders/order-detail/1.0.0} describes it.
 *
 * @param orderId the order
 * @param sku what was ordered
 * @param quantity how many
 * @param version the order's version
 * @param status {@code PLACED}, or {@code CANCELLED} once a {@code sample-orders.cancel-order} command was applied
 * @param shipment {@code PREPARED}, {@code NOT_PREPARED}, or {@code UNAVAILABLE} when the host has no sample-shipping
 * @author Brix Platform Authors
 * @since 3.3.0
 */
public record OrderDetail(String orderId, String sku, int quantity, long version, String status,
        String shipment) {
}
