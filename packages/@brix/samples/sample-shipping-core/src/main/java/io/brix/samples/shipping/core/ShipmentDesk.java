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
import java.util.function.Supplier;

import io.brix.samples.shipping.api.Shipment;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Prepares and shows shipments, in the plugin's own data.
 *
 * <p>The subscriptions call {@link #prepare(OrderPlaced)} and {@link #adjust(OrderQuantityAdjusted)} inside the unit
 * of work the runtime opened in the order's tenant, so the shipment and the inbox receipt commit together, and a
 * redelivered event never reaches them again. The two events are of different types, which are not ordered: a change
 * can arrive before the placement. Each carries the order's state and version, so whichever arrives first prepares the
 * shipment and a later one changes it only when it is newer. The capabilities come from the plugin's handler
 * factories; this class knows no transport and no infrastructure.</p>
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
     * Prepares the shipment of a placed order, joining the current unit of work. When a change of the order arrived
     * first, the shipment already shows a newer version, and the placement changes nothing.
     *
     * @param order the placed order
     * @return the shipment
     */
    public SampleShipment prepare(OrderPlaced order) {
        Objects.requireNonNull(order, "order must not be null");
        return follow(order.tenantId(), order.orderId(), order.quantity(), order.version(),
            () -> SampleShipment.prepare(Long.parseLong(ids.generate()), order, OffsetDateTime.now(clock)));
    }

    /**
     * Brings the shipment of an order to the quantity an operator set, joining the current unit of work. A change
     * that arrives before the placement prepares the shipment; one the shipment already shows, or one a later change
     * overtook, changes nothing.
     *
     * @param order the order as it is after the change
     * @return the shipment
     */
    public SampleShipment adjust(OrderQuantityAdjusted order) {
        Objects.requireNonNull(order, "order must not be null");
        return follow(order.tenantId(), order.orderId(), order.quantity(), order.version(),
            () -> SampleShipment.prepare(Long.parseLong(ids.generate()), order, OffsetDateTime.now(clock)));
    }

    private SampleShipment follow(long tenantId, String orderId, int quantity, long version,
            Supplier<SampleShipment> first) {
        return persistence.unitOfWork().required(() -> {
            SampleShipmentRepository shipments = persistence.repository(SampleShipmentRepository.class);
            Optional<SampleShipment> known = shipments.findByTenantIdAndOrderId(tenantId, orderId);
            if (known.isEmpty()) {
                return shipments.save(first.get());
            }
            SampleShipment shipment = known.get();
            return shipment.follow(quantity, version) ? shipments.save(shipment) : shipment;
        });
    }

    /**
     * Finds the shipment of an order.
     *
     * @param tenantId the order's tenant
     * @param orderId the order
     * @return its shipment, if one was prepared
     */
    public Optional<Shipment> find(long tenantId, String orderId) {
        return persistence.unitOfWork().readOnly(() -> persistence.repository(SampleShipmentRepository.class)
            .findByTenantIdAndOrderId(tenantId, orderId).map(SampleShipment::view));
    }
}
