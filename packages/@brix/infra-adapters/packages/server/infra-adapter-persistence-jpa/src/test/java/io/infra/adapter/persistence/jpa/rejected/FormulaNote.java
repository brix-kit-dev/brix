/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.rejected;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.Formula;

/** Uses a vendor annotation that embeds SQL, which the persistence API whitelist forbids. */
@Entity(name = "FormulaNote")
public class FormulaNote {

    @Id
    private Long id;

    @Formula("upper(id)")
    private String derived;
}
