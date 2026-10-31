/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.core;

import java.time.OffsetDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import io.brix.samples.shipping.api.Shipment;

/**
 * A shipment prepared for an order, in the order's tenant.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
@Entity
@Table(name = "sample_shipping_shipment")
public class SampleShipment implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "order_id", nullable = false, updatable = false, length = 32)
    private String orderId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** The version of the order this shipment reflects; a later event of another type with a lower one is stale. */
    @Column(name = "order_version", nullable = false)
    private long orderVersion;

    @Column(name = "prepared_at", nullable = false, updatable = false)
    private OffsetDateTime preparedAt;

    @Transient
    private boolean fresh = true;

    protected SampleShipment() {
    }

    /**
     * Prepares a shipment for an order.
     *
     * @param id the id the Host's generator gave
     * @param order the placed order
     * @param preparedAt when
     * @return the shipment
     */
    public static SampleShipment prepare(long id, OrderPlaced order, OffsetDateTime preparedAt) {
        Objects.requireNonNull(order, "order must not be null");
        SampleShipment shipment = new SampleShipment();
        shipment.id = id;
        shipment.tenantId = order.tenantId();
        shipment.orderId = order.orderId();
        shipment.sku = order.sku();
        shipment.quantity = order.quantity();
        shipment.orderVersion = order.version();
        shipment.preparedAt = Objects.requireNonNull(preparedAt, "preparedAt must not be null");
        return shipment;
    }

    @PostPersist
    @PostLoad
    void stored() {
        fresh = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    public long tenantId() {
        return tenantId;
    }

    public String orderId() {
        return orderId;
    }

    /**
     * Returns the shipment as the plugin shows it.
     *
     * @return its view
     */
    public Shipment view() {
        return new Shipment(orderId, sku, quantity, orderVersion);
    }
}
