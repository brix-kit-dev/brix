/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * {@code POST /api/sample-orders/orders} with {@code {"sku": ..., "quantity": ...}}: places an order in the caller's
 * tenant and publishes {@link SampleOrderPlaced} and {@link SampleOrderActivity}.
 *
 * <p>The order and {@code SampleOrderPlaced} commit together or not at all: the event is written to the plugin's
 * outbox in the same unit of work, and the relay sends it after the commit. {@code SampleOrderActivity} is sent only
 * once the unit of work has committed. The runtime has already authenticated the caller, checked the permission the
 * manifest names and bound the tenant; the handler sees them on the invocation.</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
final class PlaceOrder implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

    /** The request is not an order. */
    static final String INVALID = "SAMPLE_ORDER_INVALID";

    private final PersistenceCapability persistence;
    private final EventBusCapability events;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    PlaceOrder(PersistenceCapability persistence, EventBusCapability events, IdGeneratorCapability ids, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public EndpointResponse handle(EndpointInvocation<Object> request) {
        Map<?, ?> body = request.body() instanceof Map<?, ?> map ? map : Map.of();
        String sku = body.get("sku") instanceof String text && !text.isBlank() && text.length() <= 64 ? text : null;
        int quantity = body.get("quantity") instanceof Number number ? number.intValue() : 0;
        if (sku == null || quantity < 1 || quantity > 1000) {
            throw new EndpointHandlingException(400, INVALID, "An order needs a sku and a quantity from 1 to 1000");
        }
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        SampleOrder order = persistence.unitOfWork().required(() -> {
            SampleOrder placed = SampleOrder.place(Long.parseLong(ids.generate()), tenantId, sku, quantity,
                OffsetDateTime.now(clock));
            persistence.repository(SampleOrderRepository.class).save(placed);
            events.publishIntegration(new SampleOrderPlaced(placed));
            events.publishIntegration(new SampleOrderActivity(placed, "placed"));
            return placed;
        });
        return EndpointResponse.created(Map.of("orderId", String.valueOf(order.getId()), "version", order.version()));
    }
}
