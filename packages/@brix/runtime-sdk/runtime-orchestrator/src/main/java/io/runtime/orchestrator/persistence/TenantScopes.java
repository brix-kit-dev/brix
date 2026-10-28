/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Hands the scope the runtime chose for an entry to the persistence adapter.
 *
 * <p>The runtime requests a scope around the call that opens a Data Owner unit of work; the
 * adapter binds it when that unit of work opens a transaction, and a unit of work that joins an
 * open transaction keeps the scope it already has. Outside a request every new unit of work
 * gets {@link TenantScope#none()}, so code that bypasses the runtime cannot reach tenant data.
 * This is a private carrier of L2B and L2C (multi-tenant §11.1); plugins never touch it.</p>
 *
 * <p>In the other direction the adapter reports the tenants it binds for the requested scope, which
 * the runtime records in the platform audit of a policy use (root BRX-RS-DATA-047).</p>
 */
public final class TenantScopes {

    private static final ThreadLocal<TenantScope> REQUESTED = new ThreadLocal<>();
    private static final ThreadLocal<Set<Long>> BOUND = new ThreadLocal<>();

    private TenantScopes() {
    }

    /**
     * Runs work with a requested scope and restores the previous request afterwards.
     *
     * @param scope scope for units of work opened by the work
     * @param work work to run
     * @param <T> result type
     * @return the work's result
     */
    public static <T> T within(TenantScope scope, Supplier<T> work) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(work, "work must not be null");
        TenantScope previous = REQUESTED.get();
        Set<Long> previousBound = BOUND.get();
        REQUESTED.set(scope);
        BOUND.set(new LinkedHashSet<>());
        try {
            return work.get();
        } finally {
            restore(REQUESTED, previous);
            restore(BOUND, previousBound);
        }
    }

    /**
     * Returns the scope a new unit of work on this thread binds.
     *
     * @return the requested scope, or {@link TenantScope#none()} outside a request
     */
    public static TenantScope requested() {
        TenantScope scope = REQUESTED.get();
        return scope == null ? TenantScope.none() : scope;
    }

    /**
     * Reports a tenant the adapter bound for the requested scope. Outside a request it is ignored.
     *
     * @param tenantId bound tenant
     */
    public static void reportBound(Long tenantId) {
        Set<Long> bound = BOUND.get();
        if (bound != null && tenantId != null) {
            bound.add(tenantId);
        }
    }

    /**
     * Returns the tenants the adapter bound for the requested scope so far, in binding order.
     *
     * @return bound tenants; empty outside a request
     */
    public static List<Long> bound() {
        Set<Long> bound = BOUND.get();
        return bound == null ? List.of() : List.copyOf(bound);
    }

    private static <T> void restore(ThreadLocal<T> holder, T previous) {
        if (previous == null) {
            holder.remove();
        } else {
            holder.set(previous);
        }
    }
}
