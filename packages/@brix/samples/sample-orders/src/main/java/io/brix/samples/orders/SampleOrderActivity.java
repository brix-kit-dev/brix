/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders;

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

    /** The event type, as the manifest declares it under {@code events.publishes}. */
    public static final String TYPE = "SampleOrderActivity";

    private static final long serialVersionUID = 1L;

    private final Payload payload;

    SampleOrderActivity(SampleOrder order, String activity) {
        super(SampleOrdersPlugin.PLUGIN_ID, String.valueOf(order.tenantId()));
        this.payload = new Payload(order.tenantId(), String.valueOf(order.getId()), activity);
    }

    @Override
    public String eventTypeId() {
        return TYPE;
    }

    @Override
    public String getRoutingKey() {
        return "order." + payload.orderId();
    }

    @Override
    public Object payload() {
        return payload;
    }

    /**
     * What subscribers receive, as {@code META-INF/brix/schemas/sample-orders/sample-order-activity/1.0.0.json}
     * describes it.
     *
     * @param tenantId the tenant
     * @param orderId the order
     * @param activity what happened, such as {@code placed}
     */
    public record Payload(long tenantId, String orderId, String activity) {
    }
}
