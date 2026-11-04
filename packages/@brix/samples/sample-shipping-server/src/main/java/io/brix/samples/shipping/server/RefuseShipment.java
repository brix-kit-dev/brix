/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.samples.orders.api.CancelOrder;
import io.brix.samples.shipping.api.RefuseShipmentRequest;
import io.brix.samples.shipping.api.RefusedShipment;
import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.command.CommandClient;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * {@code POST /api/sample-shipping/shipments/{orderId}/refuse}: the warehouse cannot ship an order and asks sample-orders
 * to cancel it with the typed command {@code sample-orders.cancel-order} (root §6.1: a state change of another plugin is
 * a command; standard-link plan L4.7). It answers {@code 202}: the cancellation is queued, not done.
 *
 * <p>The runtime runs a POST handler in sample-shipping's read-write unit of work, bound to the caller's tenant (root
 * BRX-RS-DATA-028). The command is written to sample-shipping's outbox in that unit of work and reaches sample-orders
 * after the commit. Its business key is the order id, so refusing the same order again cancels it once. The handler
 * names neither the tenant nor the sender.</p>
 *
 * <p>The refusal may say why: {@code REFUSED} (the default) or {@code DAMAGED}. Refusing an order again with the
 * other reason reuses the key with other content, and sample-orders takes that command as a conflict, which the
 * cancellation's status shows (standard-link plan point 8).</p>
 */
final class RefuseShipment implements TypedEndpointHandler<RefuseShipmentRequest, RefusedShipment> {

    private final ShipmentDesk shipments;
    private final CommandClient<CancelOrder> cancellations;

    RefuseShipment(ShipmentDesk shipments, CommandClient<CancelOrder> cancellations) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
        this.cancellations = Objects.requireNonNull(cancellations, "cancellations must not be null");
    }

    @Override
    public CompletionStage<RefusedShipment> handle(EndpointInvocation<RefuseShipmentRequest> request) {
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        String orderId = request.body().orderId();
        if (shipments.find(tenantId, orderId).isEmpty()) {
            throw new SampleShippingException(SampleShippingException.Code.SHIPMENT_NOT_FOUND);
        }
        CancelOrder.Reason reason = "DAMAGED".equals(request.body().reason()) ? CancelOrder.Reason.DAMAGED
            : CancelOrder.Reason.SHIPPING_REFUSED;
        CommandSubmission cancellation = cancellations.submit(
            CancelOrder.builder().orderId(orderId).reason(reason).build(), orderId);
        return CompletableFuture.completedFuture(new RefusedShipment(orderId, cancellation.commandId()));
    }
}
