/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

import io.brix.samples.orders.api.PlaceOrderRequest;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Places orders: checks the order's rules, stores it in its tenant and publishes what happened.
 *
 * <p>The order and {@link SampleOrderPlaced} commit together or not at all: the event goes into the plugin's outbox in
 * the same unit of work, and the relay sends it after the commit. {@link SampleOrderActivity} is BEST_EFFORT: it leaves
 * only once the unit of work has committed. The capabilities come from the plugin's handler factory; this class knows
 * no transport and no infrastructure.</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class OrderPlacement {

    /** The most items one order may have. */
    public static final int MAX_QUANTITY = 1000;

    /** The longest sku. */
    public static final int MAX_SKU = 64;

    private final PersistenceCapability persistence;
    private final EventBusCapability events;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param persistence the plugin's own persistence
     * @param events the plugin's event bus
     * @param ids the Host's id generator
     * @param clock placement clock
     */
    public OrderPlacement(PersistenceCapability persistence, EventBusCapability events, IdGeneratorCapability ids,
            Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Places an order in a tenant.
     *
     * @param tenantId the tenant placing it
     * @param request what to order
     * @return the order, version 1
     * @throws InvalidOrderException when the order breaks a rule
     */
    public SampleOrder place(long tenantId, PlaceOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.sku() == null || request.sku().isBlank() || request.sku().length() > MAX_SKU) {
            throw new InvalidOrderException("An order needs a sku of at most " + MAX_SKU + " characters");
        }
        if (request.quantity() < 1 || request.quantity() > MAX_QUANTITY) {
            throw new InvalidOrderException("An order needs a quantity from 1 to " + MAX_QUANTITY);
        }
        return persistence.unitOfWork().required(() -> {
            SampleOrder placed = SampleOrder.place(Long.parseLong(ids.generate()), tenantId, request.sku(),
                request.quantity(), OffsetDateTime.now(clock));
            persistence.repository(SampleOrderRepository.class).save(placed);
            events.publishIntegration(new SampleOrderPlaced(placed));
            events.publishIntegration(new SampleOrderActivity(placed, "placed"));
            return placed;
        });
    }
}
