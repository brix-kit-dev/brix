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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.runtime.sdk.capability.IdentityTenantCapability;

/**
 * IdentityTenantCapability 实现 — 提供身份与租户成员/主体关系查询。
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C (platform-tenant) 实现, 对 Layer 2A 契约
 * ({@link IdentityTenantCapability}) 的具体实现。
 * 通过 Spring DI 注入到 Layer 1 (identity-core) 消费方。
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Service
public class IdentityTenantCapabilityImpl implements IdentityTenantCapability {

    private static final Logger log = LoggerFactory.getLogger(IdentityTenantCapabilityImpl.class);

    private final IdentityRepository identityRepository;
    private final TenantMemberRepository memberRepository;
    private final TenantPrincipalRepository principalRepository;
    private final TenantRepository tenantRepository;
    private final PlatformAdminRepository platformAdminRepository;

    public IdentityTenantCapabilityImpl(IdentityRepository identityRepository,
                                        TenantMemberRepository memberRepository,
                                        TenantPrincipalRepository principalRepository,
                                        TenantRepository tenantRepository,
                                        PlatformAdminRepository platformAdminRepository) {
        this.identityRepository = identityRepository;
        this.memberRepository = memberRepository;
        this.principalRepository = principalRepository;
        this.tenantRepository = tenantRepository;
        this.platformAdminRepository = platformAdminRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityRecord> findIdentityByEmail(String email) {
        return identityRepository.findByEmail(email)
                .map(this::toIdentityRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityRecord> findIdentityById(Long id) {
        return identityRepository.findById(id)
                .map(this::toIdentityRecord);
    }

    @Override
    @Transactional(readOnly = true)
    @CrossTenantAccess(
            reason = "Identity login flow must enumerate active tenant memberships by identity_id before a tenant is selected.",
            approval = "BRIX-ARCH-3.0.9-IDENTITY-TENANT-LOOKUP",
            readOnly = true)
    public List<TenantMembershipRecord> getActiveMemberships(Long identityId) {
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
                    m.getJoinedAt() != null ? m.getJoinedAt().toInstant() : null
            ));
        }
        return records;
    }

    @Override
    @Transactional(readOnly = true)
    @CrossTenantAccess(
            reason = "Subject login flow must enumerate active tenant principalships by identity_id before a tenant is selected.",
            approval = "BRIX-ARCH-3.0.9-IDENTITY-PRINCIPAL-LOOKUP",
            readOnly = true)
    public List<TenantPrincipalRecord> getActivePrincipalships(Long identityId) {
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
                    p.getLastAccessAt() != null ? p.getLastAccessAt().toInstant() : null
            ));
        }
        return records;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantMembershipRecord> findMembership(Long identityId, Long tenantId) {
        return memberRepository.findByTenantIdAndIdentityId(tenantId, identityId)
                .filter(TenantMember::isActive)
                .map(m -> {
                    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
                    if (tenant == null) {
                        return null;
                    }
                    return new TenantMembershipRecord(
                            m.getId(), m.getTenantId(), tenant.getCode(), tenant.getName(),
                            m.getIdentityId(), m.getMemberType().name(), m.getStatus().name(),
                            m.getJoinedAt() != null ? m.getJoinedAt().toInstant() : null
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantPrincipalRecord> findPrincipalship(Long identityId, Long tenantId) {
        return principalRepository.findByTenantIdAndIdentityId(tenantId, identityId)
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
                            p.getLastAccessAt() != null ? p.getLastAccessAt().toInstant() : null
                    );
                });
    }

    @Override
    @Transactional
    public void touchMemberAccess(Long memberId) {
        memberRepository.findById(memberId).ifPresent(m -> {
            m.setUpdatedAt(OffsetDateTime.now());
            memberRepository.save(m);
        });
    }

    @Override
    @Transactional
    public void touchPrincipalAccess(Long principalId) {
        principalRepository.findById(principalId).ifPresent(p -> {
            p.recordAccess();
            principalRepository.save(p);
        });
    }

    /**
     * S3: 写入新密码哈希并清除 {@code passwordMustChange} 标志。
     * 实现委托给 {@link IdentityRepository#updatePasswordHash}。
     */
    @Override
    @Transactional
    public void updatePasswordHash(Long identityId, String newPasswordHash) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("newPasswordHash is required");
        }
        int affected = identityRepository.updatePasswordHash(
                identityId, newPasswordHash, OffsetDateTime.now());
        if (affected == 0) {
            throw new IllegalArgumentException("Identity not found: id=" + identityId);
        }
        log.info("[IdentityTenant] password hash updated for identity={} (rows={})",
                identityId, affected);
    }

    /**
     * S3: 查询 sys_platform_admin，仅返回 status=ACTIVE 的记录。
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<PlatformAdminRecord> findActivePlatformAdmin(Long identityId) {
        if (identityId == null) {
            return Optional.empty();
        }
        return platformAdminRepository.findByIdentityId(identityId)
                .filter(PlatformAdmin::isActive)
                .map(pa -> new PlatformAdminRecord(
                        pa.getId(),
                        pa.getIdentityId(),
                        pa.getRole().name(),
                        pa.isMfaEnabled()));
    }

    private IdentityRecord toIdentityRecord(Identity identity) {
        return new IdentityRecord(
                identity.getId(),
                identity.getEmail(),
                identity.getUsername(),
                identity.getPasswordHash(),
                identity.getStatus().name(),
                identity.isPasswordMustChange(),
                identity.getTokenVersion()
        );
    }

    /**
     * A3: 递增 token_version，使该身份所有旧令牌失效。
     */
    @Override
    @Transactional
    public void incrementTokenVersion(Long identityId) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        int affected = identityRepository.incrementTokenVersion(identityId, java.time.OffsetDateTime.now());
        if (affected == 0) {
            throw new IllegalArgumentException("Identity not found: id=" + identityId);
        }
        log.info("[IdentityTenant] token_version incremented for identity={}", identityId);
    }

    /**
     * A3: 读取当前 token_version（供 JWT 颁发时嵌入 / 请求时校验）。
     */
    @Override
    @Transactional(readOnly = true)
    public long getTokenVersion(Long identityId) {
        return identityRepository.findById(identityId)
                .map(Identity::getTokenVersion)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: id=" + identityId));
    }
}
