/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.rejected;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/** Generates identifiers in the database, which the persistence API whitelist forbids. */
@Entity(name = "GeneratedNote")
public class GeneratedNote {

    @Id
    @GeneratedValue
    private Long id;
}
