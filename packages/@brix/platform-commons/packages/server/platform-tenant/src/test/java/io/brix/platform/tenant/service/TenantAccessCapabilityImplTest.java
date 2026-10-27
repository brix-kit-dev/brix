/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.enums.PrincipalType;
import io.brix.platform.tenant.enums.TenantMemberType;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.repository.TenantInvitationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.support.RecordingUnitOfWork;

/**
 * The tenant-qualified lookups the identity protocol surface uses (multi-tenant §6.4): a context
 * or an access record is reached only inside the tenant the caller names.
 */
class TenantAccessCapabilityImplTest {

    private static final UUID CONTEXT = UUID.fromString("7f6c1a52-3a0e-4f53-9d43-1b4f5d0e7a11");

    private final TenantMemberRepository members = mock(TenantMemberRepository.class);
    private final TenantPrincipalRepository principals = mock(TenantPrincipalRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();
    private final TenantAccessCapabilityImpl capability = new TenantAccessCapabilityImpl(
        members, principals, tenants, mock(TenantInvitationRepository.class), unitOfWork);

    @Test
    void anActorContextResolvesOnlyInsideItsOwnTenant() {
        TenantMember member = member();
        when(members.findByContextId(CONTEXT)).thenReturn(Optional.of(member));
        when(members.findByTenantIdAndIdentityId(7L, 5L)).thenReturn(Optional.of(member));
        when(tenants.findById(7L)).thenReturn(Optional.of(tenant()));

        var membership = capability.findMembershipByContext(7L, CONTEXT.toString()).orElseThrow();

        assertEquals(7L, membership.tenantId());
        assertEquals(CONTEXT.toString(), membership.contextId());
        assertTrue(capability.findMembershipByContext(8L, CONTEXT.toString()).isEmpty());
        assertTrue(capability.findMembershipByContext(7L, "not-a-context").isEmpty());
    }

    @Test
    void aSubjectContextResolvesOnlyInsideItsOwnTenant() {
        TenantPrincipal principal = principal();
        when(principals.findByContextId(CONTEXT)).thenReturn(Optional.of(principal));
        when(principals.findByTenantIdAndIdentityId(7L, 5L)).thenReturn(Optional.of(principal));
        when(tenants.findById(7L)).thenReturn(Optional.of(tenant()));

        assertEquals(7L, capability.findPrincipalshipByContext(7L, CONTEXT.toString()).orElseThrow().tenantId());
        assertTrue(capability.findPrincipalshipByContext(8L, CONTEXT.toString()).isEmpty());
    }

    @Test
    void accessIsRecordedOnlyInsideTheNamedTenant() {
        TenantMember member = member();
        TenantPrincipal principal = principal();
        when(members.findById(9L)).thenReturn(Optional.of(member));
        when(principals.findById(9L)).thenReturn(Optional.of(principal));

        capability.touchMemberAccess(8L, 9L);
        capability.touchPrincipalAccess(8L, 9L);
        verify(members, never()).save(any());
        verify(principals, never()).save(any());

        capability.touchMemberAccess(7L, 9L);
        capability.touchPrincipalAccess(7L, 9L);
        verify(members).save(member);
        verify(principals).save(principal);
        assertEquals(List.of("required", "required", "required", "required"), unitOfWork.modes());
    }

    private static TenantMember member() {
        TenantMember member = new TenantMember(7L, 5L, TenantMemberType.MEMBER);
        member.setId(9L);
        member.setContextId(CONTEXT);
        member.setAuthzVersion(3);
        return member;
    }

    private static TenantPrincipal principal() {
        TenantPrincipal principal = new TenantPrincipal(7L, 5L, PrincipalType.CUSTOMER);
        principal.setId(9L);
        principal.setContextId(CONTEXT);
        principal.setAuthzVersion(3);
        principal.setDisplayName("Customer");
        return principal;
    }

    private static Tenant tenant() {
        Tenant tenant = new Tenant("acme", "Acme");
        tenant.setId(7L);
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }
}
