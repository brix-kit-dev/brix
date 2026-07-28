/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

/**
 * Immutable identity of a platform operational module.
 *
 * @param moduleId descriptor module identifier
 * @param moduleVersion descriptor module version
 * @param owner descriptor owner
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OperationalModuleIdentity(String moduleId, String moduleVersion, String owner) {

    /**
     * Validates and normalizes identity fields.
     */
    public OperationalModuleIdentity {
        moduleId = requireText(moduleId, "moduleId");
        moduleVersion = requireText(moduleVersion, "moduleVersion");
        owner = requireText(owner, "owner");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
