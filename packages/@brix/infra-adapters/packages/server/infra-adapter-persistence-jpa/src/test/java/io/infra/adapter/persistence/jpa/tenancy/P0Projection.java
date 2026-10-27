/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A projection with one row per tenant: {@code tenant_id} is its identifier. */
@Entity(name = "P0Projection")
@Table(name = "p0_projection")
public class P0Projection {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    protected P0Projection() {
    }

    public P0Projection(Long tenantId, String label) {
        this.tenantId = tenantId;
        this.label = label;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
