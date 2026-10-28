/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.runtime.sdk.capability.TenantAccessCapability;

/**
 * A host that uses the registered cross-tenant policies must be able to audit them, or it does not
 * start (root BRX-RS-DATA-045, DATA-047).
 */
class PolicyAuditReadinessTest {

    private final TenantIsolationAutoConfiguration configuration = new TenantIsolationAutoConfiguration();

    @Test
    void aHostWithoutTenantAccessNeedsNoPolicyAudit() {
        assertDoesNotThrow(() -> check(false, null));
    }

    @Test
    void aHostUsingThePoliciesWithoutAWriterDoesNotStart() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> check(true, null));
        assertTrue(failure.getMessage().contains("LOGIN_TENANT_DISCOVERY"));
        assertTrue(failure.getMessage().contains("FIRST_OWNER_INVITEE_SETUP_FANOUT"));
    }

    @Test
    void aRegistrationMustCoverEveryAuditedPolicy() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> check(true, new RecordingPolicyAuditWriter(Set.of("LOGIN_TENANT_DISCOVERY"))));
        assertTrue(failure.getMessage().contains("FIRST_OWNER_INVITEE_SETUP_FANOUT"));
    }

    @Test
    void aCoveringRegistrationLetsTheHostStart() {
        assertDoesNotThrow(() -> check(true, new RecordingPolicyAuditWriter()));
    }

    @SuppressWarnings("unchecked")
    private void check(boolean tenantAccessInUse, PolicyAuditWriter writer) {
        ObjectProvider<TenantAccessCapability> tenantAccess = mock(ObjectProvider.class);
        when(tenantAccess.stream()).thenReturn(
            tenantAccessInUse ? Stream.of(mock(TenantAccessCapability.class)) : Stream.empty());
        ObjectProvider<PolicyAuditWriter> writers = mock(ObjectProvider.class);
        when(writers.getIfAvailable()).thenReturn(writer);
        configuration.policyAuditReadiness(tenantAccess, writers).afterSingletonsInstantiated();
    }
}
