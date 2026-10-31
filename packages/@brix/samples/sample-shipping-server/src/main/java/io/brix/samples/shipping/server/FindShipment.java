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

import io.brix.samples.shipping.api.FindShipmentRequest;
import io.brix.samples.shipping.api.Shipment;
import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * {@code GET /api/sample-shipping/shipments/{orderId}}: the shipment of an order in the caller's tenant.
 *
 * <p>The runtime has already checked the path variable against {@code sample-shipping/find-shipment-request},
 * authenticated the caller, checked {@code sample-shipping:shipment:read} and bound the tenant. An order of another
 * tenant is as unknown as one that does not exist: {@code sample-shipping.shipment_not_found}.</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
final class FindShipment implements TypedEndpointHandler<FindShipmentRequest, Shipment> {

    private final ShipmentDesk shipments;

    FindShipment(ShipmentDesk shipments) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
    }

    @Override
    public CompletionStage<Shipment> handle(EndpointInvocation<FindShipmentRequest> request) {
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        return CompletableFuture.completedFuture(shipments.find(tenantId, request.body().orderId())
            .orElseThrow(() -> new SampleShippingException(SampleShippingException.Code.SHIPMENT_NOT_FOUND)));
    }
}
