/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.runtime.sdk.capability.TenantCapability;
import io.runtime.sdk.capability.TenantDirectoryCapability;

/**
 * Default current-tenant directory capability implementation.
 *
 * <p>This implementation never accepts an arbitrary tenant ID. It derives the
 * tenant from {@link TenantCapability} and only then checks member/principal
 * references inside that tenant.</p>
 *
 * @since 3.2.2
 */
@Service
public class TenantDirectoryCapabilityImpl implements TenantDirectoryCapability {

    private final TenantCapability tenantCapability;
    private final TenantMemberRepository memberRepository;
    private final TenantPrincipalRepository principalRepository;

    public TenantDirectoryCapabilityImpl(TenantCapability tenantCapability,
                                         TenantMemberRepository memberRepository,
                                         TenantPrincipalRepository principalRepository) {
        this.tenantCapability = tenantCapability;
        this.memberRepository = memberRepository;
        this.principalRepository = principalRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantRef> findInCurrentTenant(RefKind refKind, Long refId) {
        if (refKind == null || refId == null) {
            return Optional.empty();
        }
        Long currentTenantId = currentTenantId();
        if (refKind == RefKind.MEMBER) {
            return memberRepository.findById(refId)
                    .filter(TenantMember::isActive)
                    .filter(m -> currentTenantId.equals(m.getTenantId()))
                    .map(this::toMemberRef);
        }
        return principalRepository.findById(refId)
                .filter(TenantPrincipal::isActive)
                .filter(p -> currentTenantId.equals(p.getTenantId()))
                .map(this::toPrincipalRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantRef> findByContextId(String contextId) {
        Optional<UUID> uuid = parseUuid(contextId);
        if (uuid.isEmpty()) {
            return Optional.empty();
        }
        Optional<TenantRef> member = memberRepository.findByContextId(uuid.get())
                .filter(TenantMember::isActive)
                .map(this::toMemberRef);
        if (member.isPresent()) {
            return member;
        }
        return principalRepository.findByContextId(uuid.get())
                .filter(TenantPrincipal::isActive)
                .map(this::toPrincipalRef);
    }

    private Long currentTenantId() {
        try {
            return Long.parseLong(tenantCapability.requireActiveTenant().id());
        } catch (NumberFormatException e) {
            throw new TenantCapability.TenantResolutionException("Current tenant ID is not numeric", e);
        }
    }

    private TenantRef toMemberRef(TenantMember member) {
        return new TenantRef(
                member.getTenantId(),
                RefKind.MEMBER,
                member.getId(),
                member.getContextId() != null ? member.getContextId().toString() : null,
                member.getStatus().name(),
                member.getMemberType().name(),
                member.getIdentityId(),
                member.getAuthzVersion() != null ? member.getAuthzVersion() : 1L);
    }

    private TenantRef toPrincipalRef(TenantPrincipal principal) {
        return new TenantRef(
                principal.getTenantId(),
                RefKind.PRINCIPAL,
                principal.getId(),
                principal.getContextId() != null ? principal.getContextId().toString() : null,
                principal.getStatus().name(),
                principal.getPrincipalType().name(),
                principal.getIdentityId(),
                principal.getAuthzVersion() != null ? principal.getAuthzVersion() : 1L);
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
