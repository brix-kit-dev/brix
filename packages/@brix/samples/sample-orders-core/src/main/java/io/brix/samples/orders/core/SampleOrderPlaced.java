/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

import io.brix.samples.orders.api.SampleOrderEvents;
import io.brix.samples.orders.api.SampleOrderPlacedPayload;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * An order was placed. The manifest publishes it STANDARD: it goes into the plugin's outbox in the unit of work that
 * placed the order and reaches subscribers at least once after the order commits. Subscribers see the events of one
 * order in the order they were published, because the routing key is the order (Kafka design D31).
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class SampleOrderPlaced extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final SampleOrderPlacedPayload payload;

    SampleOrderPlaced(SampleOrder order) {
        // The tenant makes it a TENANT event; the runtime binds the publisher itself.
        super(SampleOrderEvents.PUBLISHER, String.valueOf(order.tenantId()));
        this.payload = new SampleOrderPlacedPayload(order.tenantId(), String.valueOf(order.getId()), order.sku(),
            order.quantity(), order.version());
    }

    @Override
    public String eventTypeId() {
        return SampleOrderEvents.PLACED;
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
