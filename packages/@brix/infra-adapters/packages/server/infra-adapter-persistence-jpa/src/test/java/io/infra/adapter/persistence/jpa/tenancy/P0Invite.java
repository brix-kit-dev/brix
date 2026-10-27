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

/** Maps the keyed table of the invitation policies (multi-tenant §6.4). */
@Entity(name = "P0Invite")
@Table(name = "sys_tenant_invitation")
public class P0Invite {

    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "invitee_email", nullable = false, length = 128)
    private String inviteeEmail;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    protected P0Invite() {
    }

    public P0Invite(Long id, Long tenantId, String tokenHash, String inviteeEmail) {
        this.id = id;
        this.tenantId = tenantId;
        this.tokenHash = tokenHash;
        this.inviteeEmail = inviteeEmail;
        this.status = "PENDING";
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
