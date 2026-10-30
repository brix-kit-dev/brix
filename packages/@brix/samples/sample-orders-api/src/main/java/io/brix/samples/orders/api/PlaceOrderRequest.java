/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The body of {@code POST /api/sample-orders/orders}, as {@code brix://schemas/sample-orders/place-order-request/1.0.0}
 * describes it.
 *
 * @param sku what to order
 * @param quantity how many
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public record PlaceOrderRequest(String sku, int quantity) {
}
