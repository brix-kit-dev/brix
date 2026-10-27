/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.TenantAccessCapability;

class TenantAccessBoundaryTest {

    private final List<TenantScope> scopes = new ArrayList<>();
    private RuntimeException failure;
    private final TenantAccessCapability boundary = TenantAccessBoundary.wrap(recordingCapability());

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
                return method.getReturnType() == List.class ? List.of() : null;
            });
    }
}
