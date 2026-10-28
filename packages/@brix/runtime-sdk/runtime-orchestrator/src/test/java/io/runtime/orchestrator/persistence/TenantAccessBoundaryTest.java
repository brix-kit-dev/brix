/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import io.runtime.sdk.capability.TenantAccessCapability;
import io.runtime.sdk.capability.TenantAccessCapability.DiscoveryContext;
import io.runtime.sdk.capability.TenantAccessCapability.TenantMembershipRecord;
import io.runtime.sdk.error.PolicyAuditException;

class TenantAccessBoundaryTest {

    private final List<TenantScope> scopes = new ArrayList<>();
    private RuntimeException failure;
    private List<TenantMembershipRecord> memberships = List.of();
    private final RecordingPolicyAuditWriter writer = new RecordingPolicyAuditWriter();
    private final TenantAccessCapability boundary = TenantAccessBoundary.wrap(recordingCapability(), () -> writer);

    @AfterEach
    void clearTrace() {
        MDC.remove(PolicyUseRecord.TRACE_KEY);
    }

    @Test
    void callsThatNameATenantReachThatTenantOnly() {
        boundary.findMembership(5L, 7L);
        boundary.findPrincipalship(5L, 7L);
        boundary.findMembershipByContext(7L, "context-1");
        boundary.findPrincipalshipByContext(7L, "context-1");
        boundary.touchMemberAccess(7L, 9L);
        boundary.touchPrincipalAccess(7L, 9L);

        assertEquals(Collections.nCopies(6, TenantScope.tenant(7L)), scopes);
        assertEquals(TenantScope.none(), TenantScopes.requested(), "the scope must not outlive the call");
    }

    @Test
    void listingTheMembershipsOfAnIdentityIsLoginTenantDiscoveryKeyedByThatIdentity() {
        boundary.getActiveMemberships(5L);

        assertEquals(List.of(TenantScope.policy(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY, 5L)), scopes);
    }

    @Test
    void resolvingAFirstOwnerInvitationTakesItsKeyFromTheToken() {
        boundary.findPendingFirstOwnerInvitation("invitation-token");

        assertEquals(List.of(TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITATION_RESOLUTION, null)), scopes);
    }

    @Test
    @SuppressWarnings("removal")
    void retiredCallsReachNoTenantData() {
        boundary.getActivePrincipalships(5L);
        boundary.findMembershipByContextId("context-1");
        boundary.findPrincipalshipByContextId("context-1");
        boundary.touchMemberAccess(9L);
        boundary.touchPrincipalAccess(9L);

        assertEquals(Collections.nCopies(5, TenantScope.none()), scopes);
    }

    @Test
    void failuresReachTheCallerUnchanged() {
        failure = new IllegalStateException("rejected");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> boundary.findMembership(5L, 7L)));
        assertEquals(TenantScope.none(), TenantScopes.requested(), "the scope must not outlive the call");
    }

    @Test
    void aDiscoveryIsWrittenToThePlatformAuditBeforeItsResultIsReturned() {
        memberships = List.of(membership(910001L), membership(910002L), membership(910001L));
        MDC.put(PolicyUseRecord.TRACE_KEY, "0af7651916cd43dd8448eb211c80319c");

        List<TenantMembershipRecord> found = boundary.getActiveMemberships(5L,
            new DiscoveryContext("203.0.113.9", DiscoveryContext.Purpose.LOGIN, "hosted-login"));

        assertEquals(3, found.size());
        PolicyUseRecord record = writer.records().get(0);
        assertEquals("LOGIN_TENANT_DISCOVERY", record.action());
        assertEquals(5L, record.actorIdentityId());
        assertEquals(List.of(910001L, 910002L), record.affectedTenants());
        assertEquals(Map.of("entry", "login", "client", "hosted-login", "matched", "3"), record.context());
        assertEquals("203.0.113.0/24", record.sourceAddress());
        assertEquals("login-tenant-discovery", record.reasonCode());
        assertEquals("runtime:policy:login-tenant-discovery", record.executionIdentity());
        assertEquals("0af7651916cd43dd8448eb211c80319c", record.traceId());
    }

    @Test
    void aDiscoveryWithoutTraceGetsACorrelationIdAndNoAddress() {
        boundary.getActiveMemberships(5L);

        PolicyUseRecord record = writer.records().get(0);
        assertNotNull(record.traceId());
        assertEquals(null, record.sourceAddress());
        assertEquals(Map.of("entry", "unknown", "matched", "0"), record.context());
    }

    @Test
    void whenTheRecordCannotBeWrittenTheDiscoveryFails() {
        PolicyAuditException refused = new PolicyAuditException(Map.of("policy", "login-tenant-discovery"));
        writer.failingWith(refused);

        assertSame(refused, assertThrows(PolicyAuditException.class, () -> boundary.getActiveMemberships(5L)));
    }

    @Test
    void withoutAPolicyAuditWriterEveryDiscoveryFails() {
        TenantAccessCapability unaudited = TenantAccessBoundary.wrap(recordingCapability());

        PolicyAuditException failed = assertThrows(PolicyAuditException.class, () -> unaudited.getActiveMemberships(5L));
        assertEquals("audit.policy_use_write_failed", failed.code().wireCode());
    }

    @Test
    void aFailedDiscoveryWritesNoRecord() {
        failure = new IllegalStateException("database down");

        assertThrows(IllegalStateException.class, () -> boundary.getActiveMemberships(5L));
        assertEquals(List.of(), writer.records());
    }

    @Test
    void wrappingTwiceKeepsOneBoundary() {
        assertSame(boundary, TenantAccessBoundary.wrap(boundary));
    }

    private TenantAccessCapability recordingCapability() {
        return (TenantAccessCapability) Proxy.newProxyInstance(
            TenantAccessCapability.class.getClassLoader(),
            new Class<?>[] {TenantAccessCapability.class},
            (proxy, method, args) -> {
                scopes.add(TenantScopes.requested());
                if (failure != null) {
                    throw failure;
                }
                if (method.getReturnType() == Optional.class) {
                    return Optional.empty();
                }
                if (method.getName().equals("getActiveMemberships")) {
                    return memberships;
                }
                return method.getReturnType() == List.class ? List.of() : null;
            });
    }

    private static TenantMembershipRecord membership(Long tenantId) {
        return new TenantMembershipRecord(1L, tenantId, "code", "name", 5L, "MEMBER", "ACTIVE", null, "context", 1L);
    }
}
