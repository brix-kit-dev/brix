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

import io.brix.samples.shipping.core.OrderQuantityAdjusted;
import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * Brings each shipment to the quantity an operator set on its order, which sample-orders publishes from its internal
 * contract (standard-link plan D125).
 *
 * <p>The runtime calls it as it calls {@link PrepareShipment}: inside the plugin's unit of work, in the event's tenant,
 * with the inbox receipt in the same transaction, retried when it fails and parked after the last attempt. The order's
 * version in the payload decides whether the change is newer than what the shipment shows, because it can arrive
 * before the order's placement or after a later change.</p>
 */
final class FollowQuantity implements EventHandler<OrderQuantityAdjusted> {

    private final ShipmentDesk shipments;

    FollowQuantity(ShipmentDesk shipments) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
    }

    @Override
    public CompletionStage<Void> handle(EventInvocation<OrderQuantityAdjusted> invocation) {
        shipments.adjust(invocation.event().payload());
        return CompletableFuture.completedFuture(null);
    }
}
