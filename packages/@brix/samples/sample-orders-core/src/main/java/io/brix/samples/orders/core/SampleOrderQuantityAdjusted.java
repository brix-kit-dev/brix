/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

import io.brix.samples.orders.api.SampleOrderEvents;
import io.brix.samples.orders.api.SampleOrderQuantityAdjustedPayload;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * An operator changed how many items an order has. The manifest publishes it STANDARD: the internal contract call that
 * made the change puts it into the plugin's outbox in its own unit of work, so it commits with the change or not at
 * all, and reaches subscribers at least once after the commit (standard-link plan D125). It has the order's routing
 * key, like {@link SampleOrderPlaced}, but events of different types are not ordered: the payload carries the order's
 * state and version, and a subscriber keeps whichever is newer (Kafka design D31).
 */
public final class SampleOrderQuantityAdjusted extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final SampleOrderQuantityAdjustedPayload payload;

    SampleOrderQuantityAdjusted(SampleOrder order) {
        // The tenant makes it a TENANT event; the runtime binds the publisher itself.
        super(SampleOrderEvents.PUBLISHER, String.valueOf(order.tenantId()));
        this.payload = new SampleOrderQuantityAdjustedPayload(order.tenantId(), String.valueOf(order.getId()),
            order.sku(), order.quantity(), order.version());
    }

    @Override
    public String eventTypeId() {
        return SampleOrderEvents.QUANTITY_ADJUSTED;
    }

    @Override
    public String getRoutingKey() {
        return "order." + payload.orderId();
    }

    @Override
    public Object payload() {
        return payload;
    }
}
