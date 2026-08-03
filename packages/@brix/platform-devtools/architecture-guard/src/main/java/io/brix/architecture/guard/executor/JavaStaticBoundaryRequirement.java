/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.architecture.guard.executor;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A single Java bytecode requirement selected by module kind.
 *
 * @param id stable architecture requirement id
 * @param description human readable enforcement summary
 * @param targetSelector classes that must be present for this requirement
 * @param rule ArchUnit bytecode rule
 */
public record JavaStaticBoundaryRequirement(
    String id,
    String description,
    Predicate<JavaClass> targetSelector,
    ArchRule rule
) {

    public JavaStaticBoundaryRequirement {
        id = requireText(id, "id");
        description = requireText(description, "description");
        targetSelector = Objects.requireNonNull(targetSelector, "targetSelector");
        rule = Objects.requireNonNull(rule, "rule");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
