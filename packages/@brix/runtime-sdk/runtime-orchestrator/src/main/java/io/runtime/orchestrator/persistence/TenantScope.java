/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.Objects;
import java.util.Set;

/**
 * Tenant data a Data Owner unit of work may reach (root BRX-RS-DATA-011, BRX-RS-DATA-040).
 *
 * <p>The runtime chooses the scope from the verified entry (token {@code tid}, a registered
 * cross-tenant policy, or the typed target of a platform operation) before the unit of work
 * opens; the persistence adapter enforces it on every statement and write. Plugins never see
 * or choose it (multi-tenant T-21).</p>
 */
public sealed interface TenantScope
        permits TenantScope.Tenants, TenantScope.Policy, TenantScope.PlatformOperation, TenantScope.None {

    /**
     * Returns the scope without tenant access: tenant tables are refused.
     *
     * @return the empty scope
     */
    static TenantScope none() {
        return None.INSTANCE;
    }

    /**
     * Returns the scope of one tenant.
     *
     * @param tenantId verified tenant id
     * @return the tenant scope
     */
    static TenantScope tenant(Long tenantId) {
        return new Tenants(Set.of(Objects.requireNonNull(tenantId, "tenantId must not be null")));
    }

    /**
     * Returns the scope of a platform operation that names no tenant; it may only reach the one
     * tenant it creates (multi-tenant §6.4, platform targeted operations).
     *
     * @return the platform operation scope
     */
    static TenantScope platformOperation() {
        return PlatformOperation.INSTANCE;
    }

    /**
     * Returns the scope of a registered cross-tenant policy.
     *
     * @param policy policy registered in multi-tenant §6.4
     * @param key key bound by the runtime, or {@code null} when the request carries a secret key
     * @return the policy scope
     */
    static TenantScope policy(TenantAccessPolicy policy, Object key) {
        return new Policy(policy, key);
    }

    /** Tenant data of the given tenants only. */
    record Tenants(Set<Long> tenantIds) implements TenantScope {

        public Tenants {
            tenantIds = Set.copyOf(Objects.requireNonNull(tenantIds, "tenantIds must not be null"));
            if (tenantIds.isEmpty()) {
                throw new IllegalArgumentException("a tenant scope names at least one tenant");
            }
        }
    }

    /** A registered cross-tenant policy; the rows its key reaches decide the tenants it binds. */
    record Policy(TenantAccessPolicy policy, Object key) implements TenantScope {

        public Policy {
            Objects.requireNonNull(policy, "policy must not be null");
            if (policy.keyBoundByRuntime() && !policy.keyType().isInstance(key)) {
                throw new IllegalArgumentException("policy " + policy.id() + " needs a "
                    + policy.keyType().getSimpleName() + " key bound by the runtime");
            }
            if (!policy.keyBoundByRuntime() && key != null) {
                throw new IllegalArgumentException("policy " + policy.id() + " takes its key from the request");
            }
        }
    }

    /** A platform operation without a target tenant yet. */
    enum PlatformOperation implements TenantScope {
        INSTANCE
    }

    /** No tenant data at all. */
    enum None implements TenantScope {
        INSTANCE
    }
}
