/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.core;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The shipments, reached only through {@code PersistenceCapability.repository(...)} inside the plugin's unit of work.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public interface SampleShipmentRepository extends JpaRepository<SampleShipment, Long> {

    /**
     * Finds the shipment of an order.
     *
     * @param tenantId the order's tenant
     * @param orderId the order
     * @return its shipment, if one was prepared
     */
    Optional<SampleShipment> findByTenantIdAndOrderId(Long tenantId, String orderId);
}
