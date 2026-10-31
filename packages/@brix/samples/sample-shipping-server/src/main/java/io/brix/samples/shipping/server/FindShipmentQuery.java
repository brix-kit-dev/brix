/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.samples.shipping.api.FindShipmentRequest;
import io.brix.samples.shipping.api.Shipment;
import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.plugin.QueryInvocation;

/**
 * The typed query {@code sample-shipping.find-shipment}: the shipment of an order, for another plugin in the caller's
 * tenant (query design §4.1).
 *
 * <p>The runtime runs it in sample-shipping's own read-only unit of work, after its access policy found that the
 * caller's user may read shipments. The tenant comes from the caller's call, never from the request, and an order of
 * another tenant is as unknown as one that does not exist. Finding nothing is an empty result, not a failure.</p>
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class FindShipmentQuery implements QueryHandler<FindShipmentRequest, Optional<Shipment>> {

    private final ShipmentDesk shipments;

    FindShipmentQuery(ShipmentDesk shipments) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
    }

    @Override
    public CompletionStage<Optional<Shipment>> handle(QueryInvocation<FindShipmentRequest> query) {
        long tenantId = Long.parseLong(query.scope().tenantId().orElseThrow());
        return CompletableFuture.completedFuture(shipments.find(tenantId, query.request().orderId()));
    }
}
