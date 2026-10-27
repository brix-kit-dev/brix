/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.config.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tenant table of the auto-configured owner. */
@Entity(name = "TenantRecord")
@Table(name = "config_tenant_record")
public class TenantRecord {

    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    protected TenantRecord() {
    }

    public TenantRecord(Long id, Long tenantId, String label) {
        this.id = id;
        this.tenantId = tenantId;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
