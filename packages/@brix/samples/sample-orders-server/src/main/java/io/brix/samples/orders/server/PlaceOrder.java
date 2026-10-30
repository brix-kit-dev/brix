/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import java.util.Map;
import java.util.Objects;

import io.brix.samples.orders.api.PlaceOrderRequest;
import io.brix.samples.orders.api.PlacedOrder;
import io.brix.samples.orders.core.InvalidOrderException;
import io.brix.samples.orders.core.OrderPlacement;
import io.brix.samples.orders.core.SampleOrder;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * {@code POST /api/sample-orders/orders} with {@code {"sku": ..., "quantity": ...}}: reads the request, places the
 * order in the caller's tenant and answers with the order.
 *
 * <p>The runtime has already authenticated the caller, checked the permission the manifest names and bound the
 * tenant. The handler only adapts the protocol: the order's rules, its unit of work and its events are
 * {@link OrderPlacement}'s.</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
final class PlaceOrder implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

    /** The request is not an order. */
    static final String INVALID = "SAMPLE_ORDER_INVALID";

    private final OrderPlacement orders;

    PlaceOrder(OrderPlacement orders) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
    }

    @Override
    public EndpointResponse handle(EndpointInvocation<Object> request) {
        Map<?, ?> body = request.body() instanceof Map<?, ?> map ? map : Map.of();
        String sku = body.get("sku") instanceof String text ? text : null;
        int quantity = body.get("quantity") instanceof Number number ? number.intValue() : 0;
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        try {
            SampleOrder order = orders.place(tenantId, new PlaceOrderRequest(sku, quantity));
            return EndpointResponse.created(new PlacedOrder(String.valueOf(order.getId()), order.version()));
        } catch (InvalidOrderException invalid) {
            throw new EndpointHandlingException(400, INVALID, invalid.getMessage());
        }
    }
}
