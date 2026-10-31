/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.util.Objects;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * The scope a query runs in: the caller's tenant, or the platform when the caller is in no tenant
 * (root BRX-RS-MANIFEST-028; multi-tenant §11.1, CONTRACT-003, §11.5 item 1).
 *
 * <p>The runtime takes it from the call the caller is handling. A query never accepts a tenant from its request.</p>
 *
 * @param tenantId the caller's tenant, empty for the platform scope
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public record InvocationScope(Optional<String> tenantId) {

    private static final InvocationScope PLATFORM = new InvocationScope(Optional.empty());

    /**
     * Validates the scope.
     */
    public InvocationScope {
        tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isPresent() && tenantId.get().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    /**
     * Returns the scope of a tenant.
     *
     * @param tenantId the tenant
     * @return the scope
     */
    public static InvocationScope tenant(String tenantId) {
        return new InvocationScope(Optional.of(Objects.requireNonNull(tenantId, "tenantId must not be null")));
    }

    /**
     * Returns the platform scope.
     *
     * @return the scope without a tenant
     */
    public static InvocationScope platform() {
        return PLATFORM;
    }

    /**
     * Returns whether the scope is a tenant's.
     *
     * @return {@code true} when a tenant is present
     */
    public boolean isTenant() {
        return tenantId.isPresent();
    }
}
