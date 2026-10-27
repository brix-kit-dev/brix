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

/** Maps the tenant registry, the global table whose rows define tenants (multi-tenant §6.1). */
@Entity(name = "P0Tenant")
@Table(name = "sys_tenant")
public class P0Tenant {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    protected P0Tenant() {
    }

    public P0Tenant(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }
}
