/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.samples.orders.api.PlaceOrderRequest;
import io.brix.samples.orders.api.PlacedOrder;
import io.brix.samples.orders.core.InvalidOrderException;
import io.brix.samples.orders.core.OrderPlacement;
import io.brix.samples.orders.core.SampleOrder;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * {@code POST /api/sample-orders/orders} with {@code {"sku": ..., "quantity": ...}}: places the order in the caller's
 * tenant and answers {@code 201} with it (standard-link plan L7.9).
 *
 * <p>The runtime has already checked the body against {@code sample-orders/place-order-request}, read it as a
 * {@link PlaceOrderRequest}, authenticated the caller, checked the permission the manifest names and bound the tenant.
 * It answers with the manifest's status and checks the {@link PlacedOrder} against {@code sample-orders/placed-order}.
 * The order's rules, its unit of work and its events are {@link OrderPlacement}'s; one it refuses is
 * {@code sample-orders.order_invalid}.</p>
 *
 * <p>The manifest declares {@code idempotency.mode: required}, so the caller names a key in the {@code Idempotency-Key}
 * header. The runtime keeps the answer with the order, and a repeated call gets it again without reaching this handler
 * (standard-link plan point 10a).</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
final class PlaceOrder implements TypedEndpointHandler<PlaceOrderRequest, PlacedOrder> {

    private final OrderPlacement orders;

    PlaceOrder(OrderPlacement orders) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
    }

    @Override
    public CompletionStage<PlacedOrder> handle(EndpointInvocation<PlaceOrderRequest> request) {
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        try {
            SampleOrder order = orders.place(tenantId, request.body());
            return CompletableFuture.completedFuture(new PlacedOrder(String.valueOf(order.getId()), order.version()));
        } catch (InvalidOrderException invalid) {
            throw new SampleOrdersException(SampleOrdersException.Code.ORDER_INVALID);
        }
    }
}
