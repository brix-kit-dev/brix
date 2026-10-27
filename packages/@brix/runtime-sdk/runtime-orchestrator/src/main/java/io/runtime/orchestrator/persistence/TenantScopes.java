/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Hands the scope the runtime chose for an entry to the persistence adapter.
 *
 * <p>The runtime requests a scope around the call that opens a Data Owner unit of work; the
 * adapter binds it when that unit of work opens a transaction, and a unit of work that joins an
 * open transaction keeps the scope it already has. Outside a request every new unit of work
 * gets {@link TenantScope#none()}, so code that bypasses the runtime cannot reach tenant data.
 * This is a private carrier of L2B and L2C (multi-tenant §11.1); plugins never touch it.</p>
 */
public final class TenantScopes {

    private static final ThreadLocal<TenantScope> REQUESTED = new ThreadLocal<>();

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
        REQUESTED.set(scope);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                REQUESTED.remove();
            } else {
                REQUESTED.set(previous);
            }
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
}
