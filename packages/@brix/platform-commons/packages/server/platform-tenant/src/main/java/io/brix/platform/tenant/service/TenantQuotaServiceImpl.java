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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.dto.TenantUsageSummary;
import io.brix.platform.tenant.entity.InstallationQuota;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.exception.QuotaExceededException;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.runtime.sdk.capability.TenantQuotaCapability.InstallationQuotaSnapshot;

/**
 * Implementation of {@link TenantQuotaService}.
 *
 * <p>Enforces hard limits on tenant resource usage by querying current
 * active member/principal counts and comparing against configured quotas.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — quota enforcement implementation.</p>
 *
 * <h3>Performance</h3>
 * <p>Quota checks execute COUNT queries against indexed columns
 * (tenant_id + status). For high-throughput scenarios, consider
 * caching the counts with a short TTL.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Service
public class TenantQuotaServiceImpl implements TenantQuotaService {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaServiceImpl.class);

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository memberRepository;
    private final TenantPrincipalRepository principalRepository;
    private final InstallationQuotaRepository installationQuotaRepository;

    public TenantQuotaServiceImpl(TenantRepository tenantRepository,
                                   TenantMemberRepository memberRepository,
                                   TenantPrincipalRepository principalRepository,
                                   InstallationQuotaRepository installationQuotaRepository) {
        this.tenantRepository = tenantRepository;
        this.memberRepository = memberRepository;
        this.principalRepository = principalRepository;
        this.installationQuotaRepository = installationQuotaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkUserQuota(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        int maxUsers = tenant.getMaxUsers();
        if (maxUsers <= 0) {
            return; // unlimited
        }

        long currentUsers = memberRepository.countActiveMembers(tenantId);
        if (currentUsers >= maxUsers) {
            log.warn("User quota exceeded for tenant {}: current={}, max={}",
                    tenantId, currentUsers, maxUsers);
            throw new QuotaExceededException("maxUsers", currentUsers, maxUsers);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkPrincipalQuota(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        int maxPrincipals = tenant.getMaxPrincipals();
        if (maxPrincipals <= 0) {
            return; // unlimited
        }

        long currentPrincipals = principalRepository.countActivePrincipals(tenantId);
        if (currentPrincipals >= maxPrincipals) {
            log.warn("Principal quota exceeded for tenant {}: current={}, max={}",
                    tenantId, currentPrincipals, maxPrincipals);
            throw new QuotaExceededException("maxPrincipals", currentPrincipals, maxPrincipals);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TenantUsageSummary getUsageSummary(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        long currentUsers = memberRepository.countActiveMembers(tenantId);
        long currentPrincipals = principalRepository.countActivePrincipals(tenantId);

        return new TenantUsageSummary(
                currentUsers,
                tenant.getMaxUsers(),
                currentPrincipals,
                tenant.getMaxPrincipals()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InstallationQuotaSnapshot getInstallationQuota() {
        InstallationQuota quota = installationQuotaRepository
                .findById(InstallationQuota.DEFAULT_INSTALLATION_ID)
                .orElseGet(() -> new InstallationQuota(
                        InstallationQuota.DEFAULT_INSTALLATION_ID,
                        InstallationQuota.DEFAULT_TENANT_QUOTA,
                        0));

        Integer quotaLimit = quota.getQuota();
        Integer usedQuota = quota.getUsed();
        int limit = quotaLimit == null ? InstallationQuota.DEFAULT_TENANT_QUOTA : quotaLimit;
        int used = usedQuota == null ? 0 : usedQuota;
        boolean canCreate = used < limit;
        String refusalReason = canCreate ? null : "TENANT_QUOTA_EXCEEDED";

        return new InstallationQuotaSnapshot(
                quota.getInstallationId(),
                limit,
                used,
                "OPEN_CORE_ACTIVE",
                null,
                canCreate,
                refusalReason,
                quota.getUpdatedAt());
    }
}
