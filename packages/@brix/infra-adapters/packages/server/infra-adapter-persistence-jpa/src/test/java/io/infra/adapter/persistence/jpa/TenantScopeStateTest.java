/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantScope;

/**
 * Which scope an owner's new transaction binds from the scope the runtime requested.
 */
class TenantScopeStateTest {

    private static final TenantScope RESOLUTION =
        TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITATION_RESOLUTION, null);

    @Test
    void aPolicyStaysInForceForItsOwner() {
        TenantScopeState state = new TenantScopeState(RESOLUTION, OwnerTenantTables.none(TenantAccessPolicy.OWNER));

        assertEquals(RESOLUTION, state.scope());
    }

    @Test
    void anotherOwnerCalledUnderAPolicyReachesNoTenantData() {
        TenantScopeState state = new TenantScopeState(RESOLUTION, OwnerTenantTables.none("platform-identity"));

        assertEquals(TenantScope.none(), state.scope());
        assertTrue(state.tenants().isEmpty());
    }

    @Test
    void aVerifiedTenantAppliesToEveryOwnerOfTheRequest() {
        TenantScopeState state = new TenantScopeState(TenantScope.tenant(7L), OwnerTenantTables.none("platform-identity"));

        assertEquals(TenantScope.tenant(7L), state.scope());
    }
}
