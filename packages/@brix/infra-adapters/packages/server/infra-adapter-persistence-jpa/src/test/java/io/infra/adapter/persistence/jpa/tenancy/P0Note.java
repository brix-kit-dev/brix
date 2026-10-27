/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Tenant table on the many side of {@link P0Doc#getNotes()}. */
@Entity(name = "P0Note")
@Table(name = "p0_note")
public class P0Note {

    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_id")
    private P0Doc doc;

    @Column(name = "note_text", nullable = false, length = 64)
    private String text;

    protected P0Note() {
    }

    public P0Note(Long id, Long tenantId, P0Doc doc, String text) {
        this.id = id;
        this.tenantId = tenantId;
        this.doc = doc;
        this.text = text;
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

    public P0Doc getDoc() {
        return doc;
    }

    public String getText() {
        return text;
    }
}
