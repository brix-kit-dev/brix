/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.rejected;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Hard-codes a schema, which owner validation must reject. */
@Entity(name = "SchemaPinnedNote")
@Table(name = "pinned_note", schema = "other_owner")
public class SchemaPinnedNote {

    @Id
    private Long id;
}
