/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The answer to {@code POST /api/sample-orders/orders}, as {@code brix://schemas/sample-orders/placed-order/1.0.0}
 * describes it.
 *
 * @param orderId the order
 * @param version its version
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public record PlacedOrder(String orderId, long version) {
}
