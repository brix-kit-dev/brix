/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Tenant table with a lazy to-one association, a collection and an identity key, mapped only
 * with the annotations a plugin may use (BRX-RS-DATA-024).
 */
@Entity(name = "P0Doc")
@Table(name = "p0_doc")
public class P0Doc {

    @Id
    private Long id;

    // Deliberately updatable, so P0 can show the write guard rejecting a tenant change.
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "identity_id")
    private Long identityId;

    @Column(name = "title", nullable = false, length = 64)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private P0Folder folder;

    @OneToMany(mappedBy = "doc")
    private List<P0Note> notes = new ArrayList<>();

    protected P0Doc() {
    }

    public P0Doc(Long id, Long tenantId, Long identityId, String title, P0Folder folder) {
        this.id = id;
        this.tenantId = tenantId;
        this.identityId = identityId;
        this.title = title;
        this.folder = folder;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public P0Folder getFolder() {
        return folder;
    }

    public List<P0Note> getNotes() {
        return notes;
    }
}
