/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The cross-tenant accesses multi-tenant 5.1.2 §6.4 registers; every other cross-tenant access is
 * refused. Manifests reference a policy by its id ({@code sourcePolicyRef} or
 * {@code scopePolicyRef}) and cannot define one.
 *
 * <p>Until a policy has bound a tenant, its owner may only read the keyed table through the key.
 * The rows the key reaches bind their tenants; afterwards the policy reaches the tables listed
 * here, inside the bound tenants only.</p>
 */
public enum TenantAccessPolicy {

    /** Login-time Actor discovery: the identity's memberships in every tenant (§6.3, §9.2). */
    LOGIN_TENANT_DISCOVERY(
        "login-tenant-discovery",
        "sys_tenant_member", "identity_id", Long.class, true,
        Integer.MAX_VALUE,
        Set.of("sys_tenant_member"),
        Set.of(),
        "LOGIN_TENANT_DISCOVERY"),

    /**
     * FIRST_OWNER invitation resolution by its one-time token (§5.7, BRX-TENANT-ENTRY-005). The
     * token comes with the request, so the adapter only lets the key reach the table through an
     * equality on the unique token hash.
     */
    FIRST_OWNER_INVITATION_RESOLUTION(
        "first-owner-invitation-resolution",
        "sys_tenant_invitation", "token_hash", String.class, false,
        1,
        null,
        null,
        null),

    /** The resend after an invitee completes setup: that address's pending FIRST_OWNER invitations. */
    FIRST_OWNER_INVITEE_SETUP_FANOUT(
        "first-owner-invitee-setup-fanout",
        "sys_tenant_invitation", "invitee_email", String.class, true,
        Integer.MAX_VALUE,
        Set.of("sys_tenant_invitation", "sys_tenant_member", "biz_tenant_audit_log"),
        Set.of("sys_tenant_invitation", "biz_tenant_audit_log"),
        "FIRST_OWNER_INVITEE_SETUP_FANOUT");

    /** Owner whose tables the policies reach. */
    public static final String OWNER = "platform-tenant";

    private final String id;
    private final String keyedTable;
    private final String keyColumn;
    private final Class<?> keyType;
    private final boolean keyBoundByRuntime;
    private final int maxTenants;
    private final Set<String> readableTables;
    private final Set<String> writableTables;
    private final String auditAction;

    TenantAccessPolicy(
            String id,
            String keyedTable,
            String keyColumn,
            Class<?> keyType,
            boolean keyBoundByRuntime,
            int maxTenants,
            Set<String> readableTables,
            Set<String> writableTables,
            String auditAction) {
        this.id = id;
        this.keyedTable = keyedTable;
        this.keyColumn = keyColumn;
        this.keyType = keyType;
        this.keyBoundByRuntime = keyBoundByRuntime;
        this.maxTenants = maxTenants;
        this.readableTables = readableTables;
        this.writableTables = writableTables;
        this.auditAction = auditAction;
    }

    /**
     * Finds the policy a manifest references.
     *
     * @param id policy id
     * @return the policy, when registered
     */
    public static Optional<TenantAccessPolicy> byId(String id) {
        return Arrays.stream(values()).filter(policy -> policy.id.equals(id)).findFirst();
    }

    public String id() {
        return id;
    }

    /** Table the key reaches before any tenant is bound. */
    public String keyedTable() {
        return keyedTable;
    }

    /** Column of the keyed table the key matches. */
    public String keyColumn() {
        return keyColumn;
    }

    public Class<?> keyType() {
        return keyType;
    }

    /** Whether the runtime binds the key; otherwise the request carries it as a secret. */
    public boolean keyBoundByRuntime() {
        return keyBoundByRuntime;
    }

    /** Upper bound of the tenants the key may bind. */
    public int maxTenants() {
        return maxTenants;
    }

    /**
     * Returns whether the policy reads the table once tenants are bound.
     *
     * @param table table name
     * @return {@code true} when readable
     */
    public boolean reads(String table) {
        return readableTables == null || readableTables.contains(table);
    }

    /**
     * Returns whether the policy writes the table once tenants are bound.
     *
     * @param table table name
     * @return {@code true} when writable
     */
    public boolean writes(String table) {
        return writableTables == null || writableTables.contains(table);
    }

    /** Platform audit action the runtime records for each use, if any. */
    public Optional<String> auditAction() {
        return Optional.ofNullable(auditAction);
    }

    /** Name of the adapter's filter that applies a runtime-bound key. */
    public String keyFilterName() {
        return "brix_policy_" + name().toLowerCase(Locale.ROOT);
    }
}
