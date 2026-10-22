/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.rejected;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.infra.adapter.persistence.jpa.fixture.ForeignNote;

/** Maps an association to another owner's entity, which owner validation must reject. */
@Entity(name = "LinkedNote")
@Table(name = "linked_note")
public class LinkedNote {

    @Id
    private Long id;

    @ManyToOne
    private ForeignNote foreign;
}
