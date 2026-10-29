/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import java.util.List;

import io.runtime.orchestrator.persistence.TenantScope;

/**
 * The scope one subscriber handles one event in (multi-tenant §6.4; P4 design §7.3, §4.3).
 *
 * <p>The transport binds {@link #tenantScope()} around the subscriber's unit of work and reports the
 * tenants the persistence adapter bound in it. A scope that uses a registered cross-tenant policy records
 * the use before the subscriber commits and a failed use after the rollback.</p>
 */
public interface EventDeliveryScope {

    /**
     * The tenant scope the subscriber's unit of work binds.
     *
     * @return the scope
     */
    TenantScope tenantScope();

    /**
     * Called in the subscriber's unit of work after its handler returned, before the commit. A failure
     * here rolls the unit of work back.
     *
     * @param reached tenants the unit of work reached
     */
    default void handled(List<Long> reached) {
    }

    /**
     * Called after the unit of work committed a handled event (not a duplicate).
     *
     * @param reached tenants the unit of work reached
     */
    default void committed(List<Long> reached) {
    }

    /**
     * Called after the unit of work rolled back.
     *
     * @param reached tenants the unit of work had reached
     * @param failure what made it roll back
     */
    default void failed(List<Long> reached, RuntimeException failure) {
    }
}
