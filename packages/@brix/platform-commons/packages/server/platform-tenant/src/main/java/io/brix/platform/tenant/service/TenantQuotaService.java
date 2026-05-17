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

import io.brix.platform.tenant.dto.TenantUsageSummary;
import io.brix.platform.tenant.exception.QuotaExceededException;

/**
 * Service for tenant quota enforcement and usage tracking.
 *
 * <p>Provides hard-limit enforcement for B-side members (maxUsers) and
 * C-side principals (maxPrincipals). Quota limits are configured per-tenant
 * in sys_tenant and enforced before admitting new members or principals.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — quota enforcement service contract.</p>
 *
 * <h3>Quota Dimensions</h3>
 * <ul>
 *   <li><b>maxUsers:</b> Maximum active members in sys_tenant_member.
 *       0 = unlimited (no enforcement).</li>
 *   <li><b>maxPrincipals:</b> Maximum active principals in sys_tenant_principal.
 *       0 = unlimited (no enforcement).</li>
 * </ul>
 *
 * <h3>Enforcement Model</h3>
 * <p>Quota checks are <b>pre-admission</b> — the check runs before creating
 * the member/principal record. If the quota would be exceeded, a
 * {@link QuotaExceededException} is thrown and the admission is rejected.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public interface TenantQuotaService {

    /**
     * Checks whether adding a new member (Actor) would exceed the tenant's maxUsers quota.
     *
     * <p>If the quota limit is 0 (unlimited), this method always passes.
     * If the current active member count is already at or above the limit,
     * throws {@link QuotaExceededException}.</p>
     *
     * @param tenantId the tenant ID to check
     * @throws QuotaExceededException if the member quota would be exceeded
     */
    void checkUserQuota(Long tenantId);

    /**
     * Checks whether adding a new principal (Subject) would exceed the tenant's maxPrincipals quota.
     *
     * <p>If the quota limit is 0 (unlimited), this method always passes.
     * If the current active principal count is already at or above the limit,
     * throws {@link QuotaExceededException}.</p>
     *
     * @param tenantId the tenant ID to check
     * @throws QuotaExceededException if the principal quota would be exceeded
     */
    void checkPrincipalQuota(Long tenantId);

    /**
     * Returns a usage summary for the tenant's quotas.
     *
     * <p>Includes current counts, limits, usage percentages, and near-limit flags
     * for both member and principal dimensions.</p>
     *
     * @param tenantId the tenant ID
     * @return the usage summary
     * @throws IllegalArgumentException if tenant not found
     */
    TenantUsageSummary getUsageSummary(Long tenantId);
}
