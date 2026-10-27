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

/** Maps the keyed table of login-time discovery (multi-tenant §6.4). */
@Entity(name = "P0Member")
@Table(name = "sys_tenant_member")
public class P0Member {

    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "identity_id", nullable = false)
    private Long identityId;

    @Column(name = "member_type", nullable = false, length = 16)
    private String memberType;

    protected P0Member() {
    }

    public P0Member(Long id, Long tenantId, Long identityId, String memberType) {
        this.id = id;
        this.tenantId = tenantId;
        this.identityId = identityId;
        this.memberType = memberType;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }
}
