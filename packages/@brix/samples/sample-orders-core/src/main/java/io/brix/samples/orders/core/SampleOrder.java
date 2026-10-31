/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

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

/**
 * An order, in the tenant that placed it. The id comes from the Host's {@code IdGeneratorCapability}, never from the
 * database (root BRX-RS-DATA-024), so the entity tells Spring Data when it is new. Every row names its tenant: the
 * persistence adapter refuses a business table without {@code tenant_id}.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
@Entity
@Table(name = "sample_orders_order")
public class SampleOrder implements Persistable<Long> {

    /** An order as placed. */
    public static final String PLACED = "PLACED";

    /** An order a {@code sample-orders.cancel-order} command cancelled. */
    public static final String CANCELLED = "CANCELLED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** The aggregate's version; events carry it so subscribers can order events of different types (D31). */
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private OffsetDateTime placedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "cancel_reason", length = 32)
    private String cancelReason;

    @Transient
    private boolean fresh = true;

    protected SampleOrder() {
    }

    /**
     * Places an order.
     *
     * @param id the id the Host's generator gave
     * @param tenantId the tenant placing it
     * @param sku what is ordered
     * @param quantity how many
     * @param placedAt when
     * @return the order, version 1
     */
    public static SampleOrder place(long id, long tenantId, String sku, int quantity, OffsetDateTime placedAt) {
        SampleOrder order = new SampleOrder();
        order.id = id;
        order.tenantId = tenantId;
        order.sku = Objects.requireNonNull(sku, "sku must not be null");
        order.quantity = quantity;
        order.version = 1;
        order.placedAt = Objects.requireNonNull(placedAt, "placedAt must not be null");
        order.status = PLACED;
        return order;
    }

    /**
     * Cancels the order: it becomes {@code CANCELLED} and its version goes up. An order already cancelled stays as it
     * is.
     *
     * @param reason why it is cancelled
     * @return {@code true} when the order changed
     */
    public boolean cancel(String reason) {
        if (CANCELLED.equals(status)) {
            return false;
        }
        status = CANCELLED;
        cancelReason = Objects.requireNonNull(reason, "reason must not be null");
        version++;
        return true;
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

    public String sku() {
        return sku;
    }

    public int quantity() {
        return quantity;
    }

    public long version() {
        return version;
    }

    public OffsetDateTime placedAt() {
        return placedAt;
    }

    /**
     * Returns the order's status.
     *
     * @return {@code PLACED} or {@code CANCELLED}
     */
    public String status() {
        return status;
    }
}
