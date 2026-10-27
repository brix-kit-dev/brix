/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.tenant.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.enums.InvitationPurpose;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.repository.TenantInvitationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.runtime.sdk.capability.TenantAccessCapability;
import io.runtime.sdk.capability.TenantAccessCapability.FirstOwnerInvitationRecord;
import io.runtime.sdk.capability.TenantAccessCapability.TenantMembershipRecord;
import io.runtime.sdk.capability.TenantAccessCapability.TenantPrincipalRecord;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * Tenant-owned implementation for identity-driven tenant context discovery.
 *
 * <p>This bean owns only tenant membership/principalship reads and access-touch
 * updates. Global identity credential and platform-admin state live behind
 * {@code IdentityAccountCapability} in platform-identity.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class TenantAccessCapabilityImpl implements TenantAccessCapability {

    private final TenantMemberRepository memberRepository;
    private final TenantPrincipalRepository principalRepository;
    private final TenantRepository tenantRepository;
    private final TenantInvitationRepository invitationRepository;
    private final UnitOfWork unitOfWork;

    public TenantAccessCapabilityImpl(TenantMemberRepository memberRepository,
                                      TenantPrincipalRepository principalRepository,
                                      TenantRepository tenantRepository,
                                      TenantInvitationRepository invitationRepository,
                                      UnitOfWork unitOfWork) {
        this.memberRepository = memberRepository;
        this.principalRepository = principalRepository;
        this.tenantRepository = tenantRepository;
        this.invitationRepository = invitationRepository;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Shows a FIRST_OWNER invitation to the hosted invitation page before the invitee signs in.
     * It reads only; the acceptance entry locks the invitation, tenant and quota again and checks
     * everything anew (multi-tenant 5.1 §5.7).
     */
    @Override
    public Optional<FirstOwnerInvitationRecord> findPendingFirstOwnerInvitation(String invitationToken) {
        if (invitationToken == null || invitationToken.isBlank()) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now();
        // Hashed exactly as the acceptance entry hashes it.
        String tokenHash = SecretHashing.sha256Base64Url(invitationToken);
        return unitOfWork.readOnly(() -> invitationRepository.findByTokenHash(tokenHash)
                .filter(invitation -> invitation.getInvitationPurpose() == InvitationPurpose.FIRST_OWNER)
                .filter(invitation -> invitation.isPendingNow(now))
                .flatMap(invitation -> tenantRepository.findById(invitation.getTenantId())
                        .filter(tenant -> tenant.getStatus() == TenantStatus.PENDING_ACTIVATION)
                        .map(tenant -> new FirstOwnerInvitationRecord(
                                tenant.getId(),
                                tenant.getName(),
                                invitation.getInviteeEmail(),
                                invitation.getExpiresAt().toInstant()))));
    }

    @Override
    public List<TenantMembershipRecord> getActiveMemberships(Long identityId) {
        return unitOfWork.readOnly(() -> getActiveMembershipsInUnitOfWork(identityId));
    }

    private List<TenantMembershipRecord> getActiveMembershipsInUnitOfWork(Long identityId) {
        List<TenantMember> members = memberRepository.findActiveByIdentityId(identityId);
        if (members.isEmpty()) {
            return List.of();
        }

        // Batch load tenant info
        List<Long> tenantIds = members.stream()
                .map(TenantMember::getTenantId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Tenant> tenantMap = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        List<TenantMembershipRecord> records = new ArrayList<>();
        for (TenantMember m : members) {
            Tenant tenant = tenantMap.get(m.getTenantId());
            if (tenant == null || !tenant.isActive()) {
                continue; // 跳过已终止/暂停的租户
            }
            records.add(new TenantMembershipRecord(
                    m.getId(),
                    m.getTenantId(),
                    tenant.getCode(),
                    tenant.getName(),
                    m.getIdentityId(),
                    m.getMemberType().name(),
                    m.getStatus().name(),
                    m.getJoinedAt() != null ? m.getJoinedAt().toInstant() : null,
                    m.getContextId() != null ? m.getContextId().toString() : null,
                    m.getAuthzVersion() != null ? m.getAuthzVersion() : 1L
            ));
        }
        return records;
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public List<TenantPrincipalRecord> getActivePrincipalships(Long identityId) {
        return unitOfWork.readOnly(() -> getActivePrincipalshipsInUnitOfWork(identityId));
    }

    private List<TenantPrincipalRecord> getActivePrincipalshipsInUnitOfWork(Long identityId) {
        List<TenantPrincipal> principals = principalRepository.findActiveByIdentityId(identityId);
        if (principals.isEmpty()) {
            return List.of();
        }

        // Batch load tenant info
        List<Long> tenantIds = principals.stream()
                .map(TenantPrincipal::getTenantId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Tenant> tenantMap = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        List<TenantPrincipalRecord> records = new ArrayList<>();
        for (TenantPrincipal p : principals) {
            Tenant tenant = tenantMap.get(p.getTenantId());
            if (tenant == null || !tenant.isActive()) {
                continue;
            }
            records.add(new TenantPrincipalRecord(
                    p.getId(),
                    p.getTenantId(),
                    tenant.getCode(),
                    tenant.getName(),
                    p.getIdentityId(),
                    p.getPrincipalType().name(),
                    p.getDisplayName(),
                    p.getStatus().name(),
                    p.getLastAccessAt() != null ? p.getLastAccessAt().toInstant() : null,
                    p.getContextId() != null ? p.getContextId().toString() : null,
                    p.getAuthzVersion() != null ? p.getAuthzVersion() : 1L
            ));
        }
        return records;
    }

    @Override
    public Optional<TenantMembershipRecord> findMembership(Long identityId, Long tenantId) {
        return unitOfWork.readOnly(() -> memberRepository.findByTenantIdAndIdentityId(tenantId, identityId)
                .filter(TenantMember::isActive)
                .map(m -> {
                    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
                    if (tenant == null) {
                        return null;
                    }
                    return new TenantMembershipRecord(
                            m.getId(), m.getTenantId(), tenant.getCode(), tenant.getName(),
                            m.getIdentityId(), m.getMemberType().name(), m.getStatus().name(),
                            m.getJoinedAt() != null ? m.getJoinedAt().toInstant() : null,
                            m.getContextId() != null ? m.getContextId().toString() : null,
                            m.getAuthzVersion() != null ? m.getAuthzVersion() : 1L
                    );
                }));
    }

    @Override
    public Optional<TenantPrincipalRecord> findPrincipalship(Long identityId, Long tenantId) {
        return unitOfWork.readOnly(() -> principalRepository.findByTenantIdAndIdentityId(tenantId, identityId)
                .filter(TenantPrincipal::isActive)
                .map(p -> {
                    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
                    if (tenant == null) {
                        return null;
                    }
                    return new TenantPrincipalRecord(
                            p.getId(), p.getTenantId(), tenant.getCode(), tenant.getName(),
                            p.getIdentityId(), p.getPrincipalType().name(), p.getDisplayName(),
                            p.getStatus().name(),
                            p.getLastAccessAt() != null ? p.getLastAccessAt().toInstant() : null,
                            p.getContextId() != null ? p.getContextId().toString() : null,
                            p.getAuthzVersion() != null ? p.getAuthzVersion() : 1L
                    );
                }));
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public Optional<TenantMembershipRecord> findMembershipByContextId(String contextId) {
        return parseUuid(contextId).flatMap(id -> unitOfWork.readOnly(() -> memberRepository.findByContextId(id)
                .filter(TenantMember::isActive)
                .flatMap(m -> findMembership(m.getIdentityId(), m.getTenantId()))));
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public Optional<TenantPrincipalRecord> findPrincipalshipByContextId(String contextId) {
        return parseUuid(contextId).flatMap(id -> unitOfWork.readOnly(() -> principalRepository.findByContextId(id)
                .filter(TenantPrincipal::isActive)
                .flatMap(p -> findPrincipalship(p.getIdentityId(), p.getTenantId()))));
    }

    @Override
    public Optional<TenantMembershipRecord> findMembershipByContext(Long tenantId, String contextId) {
        return parseUuid(contextId).flatMap(id -> unitOfWork.readOnly(() -> memberRepository.findByContextId(id)
                .filter(TenantMember::isActive)
                .filter(m -> m.getTenantId().equals(tenantId))
                .flatMap(m -> findMembership(m.getIdentityId(), tenantId))));
    }

    @Override
    public Optional<TenantPrincipalRecord> findPrincipalshipByContext(Long tenantId, String contextId) {
        return parseUuid(contextId).flatMap(id -> unitOfWork.readOnly(() -> principalRepository.findByContextId(id)
                .filter(TenantPrincipal::isActive)
                .filter(p -> p.getTenantId().equals(tenantId))
                .flatMap(p -> findPrincipalship(p.getIdentityId(), tenantId))));
    }

    @Override
    public void touchMemberAccess(Long tenantId, Long memberId) {
        unitOfWork.required(() -> {
            memberRepository.findById(memberId)
                    .filter(m -> m.getTenantId().equals(tenantId))
                    .ifPresent(m -> {
                        m.setUpdatedAt(OffsetDateTime.now());
                        memberRepository.save(m);
                    });
            return null;
        });
    }

    @Override
    public void touchPrincipalAccess(Long tenantId, Long principalId) {
        unitOfWork.required(() -> {
            principalRepository.findById(principalId)
                    .filter(p -> p.getTenantId().equals(tenantId))
                    .ifPresent(p -> {
                        p.recordAccess();
                        principalRepository.save(p);
                    });
            return null;
        });
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void touchMemberAccess(Long memberId) {
        unitOfWork.required(() -> {
            memberRepository.findById(memberId).ifPresent(m -> {
                m.setUpdatedAt(OffsetDateTime.now());
                memberRepository.save(m);
            });
            return null;
        });
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public void touchPrincipalAccess(Long principalId) {
        unitOfWork.required(() -> {
            principalRepository.findById(principalId).ifPresent(p -> {
                p.recordAccess();
                principalRepository.save(p);
            });
            return null;
        });
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
