/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.util.Map;
import java.util.Objects;

import io.brix.samples.orders.api.CancelOrder;
import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.command.CommandClient;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * {@code POST /api/sample-shipping/shipments/{orderId}/refuse}: the warehouse cannot ship an order and asks sample-orders
 * to cancel it with the typed command {@code sample-orders.cancel-order} (root §6.1: a state change of another plugin is
 * a command; standard-link plan L4.7).
 *
 * <p>The runtime runs a POST handler in sample-shipping's read-write unit of work, bound to the caller's tenant (root
 * BRX-RS-DATA-028). The command is written to sample-shipping's outbox in that unit of work and reaches sample-orders
 * after the commit. Its business key is the order id, so refusing the same order again cancels it once. The handler
 * names neither the tenant nor the sender.</p>
 */
final class RefuseShipment implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

    private final ShipmentDesk shipments;
    private final CommandClient<CancelOrder> cancellations;

    RefuseShipment(ShipmentDesk shipments, CommandClient<CancelOrder> cancellations) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
        this.cancellations = Objects.requireNonNull(cancellations, "cancellations must not be null");
    }

    @Override
    public EndpointResponse handle(EndpointInvocation<Object> request) {
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        String orderId = request.pathVariables().get("orderId");
        if (shipments.find(tenantId, orderId).isEmpty()) {
            throw new EndpointHandlingException(404, FindShipment.NOT_FOUND, "The order has no shipment");
        }
        CommandSubmission cancellation = cancellations.submit(
            CancelOrder.builder().orderId(orderId).reason(CancelOrder.Reason.SHIPPING_REFUSED).build(), orderId);
        return new EndpointResponse(202, Map.of("orderId", orderId, "commandId", cancellation.commandId()));
    }
}
