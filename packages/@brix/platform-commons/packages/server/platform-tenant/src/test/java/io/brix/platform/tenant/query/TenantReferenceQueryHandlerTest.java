/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.api.TenantReference;
import io.brix.platform.tenant.api.TenantReferenceQuery;
import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.enums.MemberStatus;
import io.brix.platform.tenant.enums.PrincipalStatus;
import io.brix.platform.tenant.enums.PrincipalType;
import io.brix.platform.tenant.enums.TenantMemberType;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.QueryInvocation;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Multi-tenant §7.1 and {@code BRX-TENANT-CONTRACT-003}: a reference is looked up in the caller's
 * tenant only, by its kind, and says no more than whether it is active.
 */
class TenantReferenceQueryHandlerTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final TenantMemberRepository members = mock(TenantMemberRepository.class);
    private final TenantPrincipalRepository principals = mock(TenantPrincipalRepository.class);
    private final TenantReferenceQueryHandler handler = new TenantReferenceQueryHandler(members, principals);

    @Test
    void anActiveMemberOfTheCallersTenantIsFoundActive() {
        when(members.findByIdAndTenantId(7L, 100L)).thenReturn(Optional.of(member(MemberStatus.ACTIVE)));

        assertEquals(Optional.of(new TenantReference(true)), answer(7L, TenantReferenceQuery.RefKind.MEMBER, "100"));
    }

    @Test
    void aSuspendedMemberIsFoundButNotActive() {
        when(members.findByIdAndTenantId(7L, 100L)).thenReturn(Optional.of(member(MemberStatus.SUSPENDED)));

        assertEquals(Optional.of(new TenantReference(false)), answer(7L, TenantReferenceQuery.RefKind.MEMBER, "100"));
    }

    @Test
    void anIdOfAnotherTenantIsNotFound() {
        // The member exists in tenant 200 only; the lookup takes the caller's tenant, so it finds nothing.
        when(members.findByIdAndTenantId(7L, 200L)).thenReturn(Optional.of(member(MemberStatus.ACTIVE)));
        when(members.findByIdAndTenantId(7L, 100L)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), answer(7L, TenantReferenceQuery.RefKind.MEMBER, "100"));
    }

    @Test
    void aPrincipalIsLookedUpAsAPrincipalEvenWhenAMemberHasTheSameId() {
        when(principals.findByIdAndTenantId(7L, 100L)).thenReturn(Optional.of(principal(PrincipalStatus.REVOKED)));

        assertEquals(Optional.of(new TenantReference(false)),
            answer(7L, TenantReferenceQuery.RefKind.PRINCIPAL, "100"));
        verifyNoInteractions(members);
    }

    private Optional<TenantReference> answer(long refId, TenantReferenceQuery.RefKind kind, String tenantId) {
        TenantReferenceQuery request = TenantReferenceQuery.builder().refId(refId).refKind(kind).build();
        QueryInvocation<TenantReferenceQuery> invocation = new QueryInvocation<>(request,
            CallerIdentity.service("medical-case"), InvocationScope.tenant(tenantId), AccessDecision.none(),
            new TraceContext(TRACEPARENT, null), Instant.now().plusSeconds(2), CancellationSignal.none());
        return handler.handle(invocation).toCompletableFuture().join();
    }

    private static TenantMember member(MemberStatus status) {
        TenantMember member = new TenantMember(100L, 9L, TenantMemberType.MEMBER);
        member.setStatus(status);
        return member;
    }

    private static TenantPrincipal principal(PrincipalStatus status) {
        TenantPrincipal principal = new TenantPrincipal(100L, 9L, PrincipalType.CUSTOMER);
        principal.setStatus(status);
        return principal;
    }
}
