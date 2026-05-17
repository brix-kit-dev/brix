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
package io.brix.platform.tenant.dto;

/**
 * Summary of tenant resource usage against configured quotas.
 *
 * <p>Provides a snapshot of current usage vs. quota limits for both
 * B-side members (Actor) and C-side principals (Subject).</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons DTO.</p>
 *
 * <h3>Usage Percentage</h3>
 * <p>When the quota limit is 0 (unlimited), the usage percentage is 0.0
 * and {@code nearLimit} is always false.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see io.brix.platform.tenant.service.TenantQuotaService
 */
public class TenantUsageSummary {

    private final long currentUsers;
    private final int maxUsers;
    private final double userUsagePercent;
    private final boolean userNearLimit;

    private final long currentPrincipals;
    private final int maxPrincipals;
    private final double principalUsagePercent;
    private final boolean principalNearLimit;

    /**
     * Constructs a usage summary.
     *
     * @param currentUsers       current active member count
     * @param maxUsers           maximum member quota (0 = unlimited)
     * @param currentPrincipals  current active principal count
     * @param maxPrincipals      maximum principal quota (0 = unlimited)
     */
    public TenantUsageSummary(long currentUsers, int maxUsers,
                               long currentPrincipals, int maxPrincipals) {
        this.currentUsers = currentUsers;
        this.maxUsers = maxUsers;
        this.userUsagePercent = maxUsers > 0
                ? (double) currentUsers / maxUsers * 100.0
                : 0.0;
        this.userNearLimit = maxUsers > 0 && userUsagePercent > 80.0;

        this.currentPrincipals = currentPrincipals;
        this.maxPrincipals = maxPrincipals;
        this.principalUsagePercent = maxPrincipals > 0
                ? (double) currentPrincipals / maxPrincipals * 100.0
                : 0.0;
        this.principalNearLimit = maxPrincipals > 0 && principalUsagePercent > 80.0;
    }

    public long getCurrentUsers() {
        return currentUsers;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public double getUserUsagePercent() {
        return userUsagePercent;
    }

    public boolean isUserNearLimit() {
        return userNearLimit;
    }

    public long getCurrentPrincipals() {
        return currentPrincipals;
    }

    public int getMaxPrincipals() {
        return maxPrincipals;
    }

    public double getPrincipalUsagePercent() {
        return principalUsagePercent;
    }

    public boolean isPrincipalNearLimit() {
        return principalNearLimit;
    }
}
