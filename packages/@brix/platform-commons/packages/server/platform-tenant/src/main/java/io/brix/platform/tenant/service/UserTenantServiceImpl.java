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

import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * User-facing Tenant Service implementation.
 *
 * <p>Resolves available tenants for an authenticated user by querying
 * the {@link TenantMemberRepository} for active memberships and
 * joining with the {@link TenantRepository} for tenant details.</p>
 *
 * <h3>Fallback Strategy</h3>
 * <p>When no memberships exist for the identity (e.g., user created via OAuth2
 * before platform-level Identity ↔ TenantMember linking), the service falls
 * back to returning the tenant identified by the JWT's {@code tenant_id} claim.
 * This ensures the frontend always receives at least one available tenant.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — multi-tenant capability implementation</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@Service
public class UserTenantServiceImpl implements UserTenantService {

    private static final Logger log = LoggerFactory.getLogger(UserTenantServiceImpl.class);

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;

    public UserTenantServiceImpl(TenantRepository tenantRepository,
                                  TenantMemberRepository tenantMemberRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @CrossTenantAccess(
            reason = "Tenant selector must enumerate active memberships by identity_id before the user chooses a tenant.",
            approval = "BRIX-ARCH-3.0.9-TENANT-SELECTOR",
            readOnly = true)
    public List<Tenant> getAvailableTenants(String identityId, String currentTenantId) {
        // Strategy 1: Resolve via membership if identity ID is numeric (Snowflake ID)
        if (identityId != null && !identityId.isBlank()) {
            try {
                Long id = Long.parseLong(identityId);
                List<TenantMember> memberships = tenantMemberRepository.findActiveByIdentityId(id);

                if (!memberships.isEmpty()) {
                    List<Long> tenantIds = memberships.stream()
                            .map(TenantMember::getTenantId)
                            .distinct()
                            .toList();

                    List<Tenant> tenants = tenantRepository.findAllById(tenantIds).stream()
                            .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
                            .toList();

                    if (!tenants.isEmpty()) {
                        log.debug("Resolved {} available tenants for identity={}", tenants.size(), identityId);
                        return tenants;
                    }
                }
            } catch (NumberFormatException e) {
                log.debug("Identity ID '{}' is not numeric, falling back to tenant lookup", identityId);
            }
        }

        // Strategy 2: Fallback to current tenant from JWT claims
        if (currentTenantId != null && !currentTenantId.isBlank()) {
            Optional<Tenant> currentTenant = findTenantByIdOrCode(currentTenantId);
            if (currentTenant.isPresent() && currentTenant.get().getStatus() == TenantStatus.ACTIVE) {
                log.debug("Fallback: returning current tenant '{}' for identity={}",
                        currentTenantId, identityId);
                return List.of(currentTenant.get());
            }
        }

        // Strategy 3: Last resort — return all active tenants (platform-level access)
        log.debug("No membership or tenant claim found, returning all active tenants");
        return tenantRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> getTenantById(String tenantId) {
        return findTenantByIdOrCode(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    @CrossTenantAccess(
            reason = "Tenant switch validation may inspect identity memberships across tenants before issuing a new tenant context.",
            approval = "BRIX-ARCH-3.0.9-TENANT-SWITCH",
            readOnly = true)
    public Tenant switchTenant(String identityId, String targetTenantId, String currentTenantId) {
        Tenant target = findTenantByIdOrCode(targetTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + targetTenantId));

        if (target.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalArgumentException("Tenant is not active: " + targetTenantId);
        }

        // Validate access: check membership if identity is known
        if (identityId != null && !identityId.isBlank()) {
            try {
                Long id = Long.parseLong(identityId);
                boolean hasAccess = tenantMemberRepository.isActiveMember(target.getId(), id);
                if (!hasAccess) {
                    // Check if there are ANY memberships — if none, allow (pre-linking phase)
                    List<TenantMember> anyMemberships = tenantMemberRepository.findByIdentityId(id);
                    if (!anyMemberships.isEmpty()) {
                        throw new SecurityException(
                                "Identity " + identityId + " has no access to tenant " + targetTenantId);
                    }
                    log.debug("Identity {} has no memberships, allowing access to tenant {} (pre-linking phase)",
                            identityId, targetTenantId);
                }
            } catch (NumberFormatException e) {
                log.debug("Cannot verify membership for non-numeric identity: {}", identityId);
            }
        }

        log.info("Tenant switch: identity={}, from={}, to={}", identityId, currentTenantId, targetTenantId);
        return target;
    }

    /**
     * Finds a tenant by ID (Long) or by code (String).
     *
     * <p>First tries to parse as Long for ID lookup, then falls back to code lookup.
     * This handles both numeric IDs (e.g., "1001") and string codes (e.g., "acme-corp").</p>
     */
    private Optional<Tenant> findTenantByIdOrCode(String tenantIdOrCode) {
        if (tenantIdOrCode == null || tenantIdOrCode.isBlank()) {
            return Optional.empty();
        }
        try {
            Long id = Long.parseLong(tenantIdOrCode);
            return tenantRepository.findById(id);
        } catch (NumberFormatException e) {
            return tenantRepository.findByCode(tenantIdOrCode);
        }
    }
}
