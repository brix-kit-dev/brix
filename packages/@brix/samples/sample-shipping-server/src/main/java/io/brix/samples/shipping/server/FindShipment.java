/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.util.Objects;

import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * {@code GET /api/sample-shipping/shipments/{orderId}}: the shipment of an order in the caller's tenant.
 *
 * <p>The runtime has already authenticated the caller, checked {@code sample-shipping:shipment:read} and bound the
 * tenant. An order of another tenant is as unknown as one that does not exist.</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
final class FindShipment implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

    /** The order has no shipment in the caller's tenant. */
    static final String NOT_FOUND = "SAMPLE_SHIPMENT_NOT_FOUND";

    private final ShipmentDesk shipments;

    FindShipment(ShipmentDesk shipments) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
    }

    @Override
    public EndpointResponse handle(EndpointInvocation<Object> request) {
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        String orderId = request.pathVariables().get("orderId");
        return shipments.find(tenantId, orderId).map(EndpointResponse::ok).orElseThrow(
            () -> new EndpointHandlingException(404, NOT_FOUND, "The order has no shipment"));
    }
}
