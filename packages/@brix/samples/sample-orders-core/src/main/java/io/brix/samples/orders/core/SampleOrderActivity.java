/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

import io.brix.samples.orders.api.SampleOrderActivityPayload;
import io.brix.samples.orders.api.SampleOrderEvents;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Something happened to an order that is worth a glance but not a guarantee, such as for an activity feed. The
 * manifest publishes it BEST_EFFORT: it skips the outbox, leaves after the unit of work commits and is not retried.
 * Nothing that must happen may depend on it.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class SampleOrderActivity extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final SampleOrderActivityPayload payload;

    SampleOrderActivity(SampleOrder order, String activity) {
        super(SampleOrderEvents.PUBLISHER, String.valueOf(order.tenantId()));
        this.payload = new SampleOrderActivityPayload(order.tenantId(), String.valueOf(order.getId()), activity);
    }

    @Override
    public String eventTypeId() {
        return SampleOrderEvents.ACTIVITY;
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
