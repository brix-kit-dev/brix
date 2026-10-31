/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The request of {@code GET /api/sample-orders/orders/{orderId}}, as {@code sample-orders/find-order-request} describes
 * it: its path variable.
 *
 * @param orderId the order
 */
public record FindOrderRequest(String orderId) {
}
