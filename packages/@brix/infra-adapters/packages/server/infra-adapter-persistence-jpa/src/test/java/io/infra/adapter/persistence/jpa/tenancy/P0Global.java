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

/** Global table without tenant_id, like sys_tenant. */
@Entity(name = "P0Global")
@Table(name = "p0_global")
public class P0Global {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    protected P0Global() {
    }

    public P0Global(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
