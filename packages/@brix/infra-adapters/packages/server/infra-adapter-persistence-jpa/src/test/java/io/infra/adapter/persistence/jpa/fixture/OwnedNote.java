/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.fixture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity(name = "OwnedNote")
@Table(name = "owned_note", uniqueConstraints = @UniqueConstraint(name = "uk_owned_note_code", columnNames = "code"))
public class OwnedNote {

    @Id
    private Long id;

    @Version
    private long version;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "note_text", length = 255)
    private String text;

    protected OwnedNote() {
    }

    public OwnedNote(Long id, String code, String text) {
        this.id = id;
        this.code = code;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public String getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
