/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.samples.orders.api.FindOrderRequest;
import io.brix.samples.orders.api.OrderDetail;
import io.brix.samples.orders.core.OrderPlacement;
import io.brix.samples.orders.core.SampleOrder;
import io.brix.samples.shipping.api.FindShipmentRequest;
import io.brix.samples.shipping.api.Shipment;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;
import io.runtime.sdk.query.BrixQueryException;
import io.runtime.sdk.query.QueryClient;

/**
 * {@code GET /api/sample-orders/orders/{orderId}}: an order of the caller's tenant and how far its shipment is.
 *
 * <p>The order is sample-orders' own data. Its shipment belongs to sample-shipping, so the handler asks for it through
 * the typed query {@code sample-shipping.find-shipment} (query design §4.2) and never reads sample-shipping's tables.
 * The runtime gives the query this request's tenant, user and deadline. The query is optional: a host without
 * sample-shipping answers {@code PROVIDER_MISSING}, and the order is shown with its shipment {@code UNAVAILABLE},
 * not as one that has none (root BRX-RS-RESILIENCE-005). Every other query failure fails the request. An order the
 * tenant does not have is {@code sample-orders.order_not_found}.</p>
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class ShowOrder implements TypedEndpointHandler<FindOrderRequest, OrderDetail> {

    private final OrderPlacement orders;
    private final QueryClient<FindShipmentRequest, Optional<Shipment>> shipments;

    ShowOrder(OrderPlacement orders, QueryClient<FindShipmentRequest, Optional<Shipment>> shipments) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
    }

    @Override
    public CompletionStage<OrderDetail> handle(EndpointInvocation<FindOrderRequest> request) {
        String orderId = request.body().orderId();
        SampleOrder order = parse(orderId).flatMap(orders::find)
            .orElseThrow(() -> new SampleOrdersException(SampleOrdersException.Code.ORDER_NOT_FOUND));
        return CompletableFuture.completedFuture(new OrderDetail(orderId, order.sku(), order.quantity(),
            order.version(), order.status(), shipmentOf(orderId)));
    }

    private String shipmentOf(String orderId) {
        try {
            return shipments.execute(FindShipmentRequest.builder().orderId(orderId).build()).isPresent()
                ? "PREPARED" : "NOT_PREPARED";
        } catch (BrixQueryException failure) {
            if (failure.is(BrixQueryException.Code.PROVIDER_MISSING)) {
                return "UNAVAILABLE";
            }
            throw failure;
        }
    }

    private static Optional<Long> parse(String orderId) {
        try {
            return Optional.of(Long.parseLong(orderId));
        } catch (NumberFormatException notAnOrder) {
            return Optional.empty();
        }
    }
}
