/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.Set;

/**
 * Tables the tenant filter treats specially, as multi-tenant 5.1.2 §6.1 lists them.
 */
public final class TenantIsolationRegister {

    /** Column that names a row's tenant. */
    public static final String TENANT_COLUMN = "tenant_id";

    /**
     * Global tables, one by one (§6.1, red line T-15). A table without {@code tenant_id} that is not
     * listed here, and is not an outbox or inbox its owner declares, fails startup.
     */
    public static final Set<String> GLOBAL_TABLES = Set.of(
        "sys_tenant",
        "sys_identity",
        "sys_platform_admin",
        "sys_platform_config",
        "sys_installation_quota",
        "sys_platform_audit_log",
        "sys_setup_token",
        "sys_bootstrap_state");

    /** The global table whose rows define the tenants (§6.1). */
    public static final String TENANT_REGISTRY_TABLE = "sys_tenant";

    private TenantIsolationRegister() {
    }
}
