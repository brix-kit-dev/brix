/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.util.Objects;

import io.runtime.sdk.annotation.Since;

/**
 * Who a task runs as (root BRX-RS-MANIFEST-025, MANIFEST-028; standard-link plan L6.1). The runtime builds it from the
 * task's {@code executionIdentityPolicyRef}, and the task cannot replace it: a task acts as its module, with no user,
 * no token and nobody's permissions.
 *
 * @param moduleId the plugin or platform operational module the task belongs to
 * @param kind how the task runs
 * @since 3.3.0
 */
@Since("3.3.0")
public record ExecutionIdentity(String moduleId, Kind kind) {

    /** How a task runs. A later minor version may add kinds. */
    public enum Kind {
        /** As its module's own service identity ({@code plugin-service}). */
        SERVICE
    }

    /**
     * Creates the identity; the runtime builds it.
     */
    public ExecutionIdentity {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId must not be blank");
        }
        Objects.requireNonNull(kind, "kind must not be null");
    }

    /**
     * Returns the service identity of a module.
     *
     * @param moduleId the module
     * @return its service identity
     */
    public static ExecutionIdentity service(String moduleId) {
        return new ExecutionIdentity(moduleId, Kind.SERVICE);
    }
}
