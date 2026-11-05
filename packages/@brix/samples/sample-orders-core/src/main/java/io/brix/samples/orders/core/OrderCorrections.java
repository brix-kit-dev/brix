/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

import java.util.Objects;

import io.brix.samples.orders.internal.AdjustOrderQuantity;
import io.brix.samples.orders.internal.AdjustedOrder;
import io.brix.samples.orders.internal.SampleOrderAdministration;
import io.brix.samples.orders.internal.SampleOrderAdministrationException;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * What the platform's operators may do to an order. The runtime calls it in sample-orders' unit of work, so it opens
 * none; the tenant filter hides every order outside the tenant the command names.
 */
public final class OrderCorrections implements SampleOrderAdministration {

    private final PersistenceCapability persistence;

    /**
     * Creates the service.
     *
     * @param persistence the plugin's own persistence
     */
    public OrderCorrections(PersistenceCapability persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
    }

    @Override
    public AdjustedOrder adjustQuantity(AdjustOrderQuantity command) {
        Objects.requireNonNull(command, "command must not be null");
        SampleOrderRepository orders = persistence.repository(SampleOrderRepository.class);
        SampleOrder order = orders.findById(command.orderId()).orElseThrow(() -> new SampleOrderAdministrationException(
            SampleOrderAdministrationException.ORDER_NOT_FOUND, "The tenant has no such order"));
        if (!order.adjustQuantity(command.delta(), OrderPlacement.MAX_QUANTITY)) {
            throw new SampleOrderAdministrationException(SampleOrderAdministrationException.QUANTITY_INVALID,
                "An order has from 1 to " + OrderPlacement.MAX_QUANTITY + " items");
        }
        SampleOrder saved = orders.save(order);
        return new AdjustedOrder(String.valueOf(saved.getId()), saved.quantity(), saved.version());
    }
}
