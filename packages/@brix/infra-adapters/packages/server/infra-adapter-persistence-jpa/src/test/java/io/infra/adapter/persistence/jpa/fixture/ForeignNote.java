/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.fixture;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stands in for an entity owned by another data owner. */
@Entity(name = "ForeignNote")
@Table(name = "foreign_note")
public class ForeignNote {

    @Id
    private Long id;

    private String label;

    protected ForeignNote() {
    }

    public ForeignNote(Long id, String label) {
        this.id = id;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
