/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.core;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import io.brix.samples.shipping.api.ShipmentView;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Prepares and shows shipments, in the plugin's own data.
 *
 * <p>The subscription calls {@link #prepare(OrderPlaced)} inside the unit of work the runtime opened in the order's
 * tenant, so the shipment and the inbox receipt commit together, and a redelivered event never reaches it again. The
 * capabilities come from the plugin's handler factories; this class knows no transport and no infrastructure.</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class ShipmentDesk {

    private final PersistenceCapability persistence;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param persistence the plugin's own persistence
     * @param ids the Host's id generator
     * @param clock preparation clock
     */
    public ShipmentDesk(PersistenceCapability persistence, IdGeneratorCapability ids, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Prepares the shipment of a placed order, joining the current unit of work.
     *
     * @param order the placed order
     * @return the shipment
     */
    public SampleShipment prepare(OrderPlaced order) {
        Objects.requireNonNull(order, "order must not be null");
        return persistence.unitOfWork().required(() -> persistence.repository(SampleShipmentRepository.class)
            .save(SampleShipment.prepare(Long.parseLong(ids.generate()), order, OffsetDateTime.now(clock))));
    }

    /**
     * Finds the shipment of an order.
     *
     * @param tenantId the order's tenant
     * @param orderId the order
     * @return its shipment, if one was prepared
     */
    public Optional<ShipmentView> find(long tenantId, String orderId) {
        return persistence.unitOfWork().readOnly(() -> persistence.repository(SampleShipmentRepository.class)
            .findByTenantIdAndOrderId(tenantId, orderId).map(SampleShipment::view));
    }
}
