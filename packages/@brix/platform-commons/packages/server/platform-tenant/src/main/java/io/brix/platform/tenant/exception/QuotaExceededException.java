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
package io.brix.platform.tenant.exception;

import io.brix.platform.common.exception.BusinessException;

/**
 * Exception thrown when a tenant quota limit is exceeded.
 *
 * <p>Indicates that the requested operation would exceed a configured hard
 * limit, including tenant-internal member/principal quotas or the
 * installation-level tenant count quota.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — quota enforcement exception.</p>
 *
 * <h3>Quota Dimensions</h3>
 * <ul>
 *   <li><b>maxUsers:</b> Maximum B-side Actor members in sys_tenant_member</li>
 *   <li><b>maxPrincipals:</b> Maximum C-side Subject principals in sys_tenant_principal</li>
 *   <li><b>installationTenants:</b> Maximum active tenants for this deployment instance</li>
 * </ul>
 *
 * <h3>HTTP Mapping</h3>
 * <p>Should be mapped to HTTP 429 Too Many Requests or 403 Forbidden
 * by the global exception handler.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see io.brix.platform.tenant.service.TenantQuotaService
 */
public class QuotaExceededException extends BusinessException {

    private final String quotaDimension;
    private final long currentUsage;
    private final long maxAllowed;

    /**
     * Creates a QuotaExceededException.
     *
    * @param quotaDimension the quota dimension that was exceeded
     * @param currentUsage   the current usage count
     * @param maxAllowed     the maximum allowed count
     */
    public QuotaExceededException(String quotaDimension, long currentUsage, long maxAllowed) {
        super("QUOTA_EXCEEDED",
              String.format("Tenant quota exceeded for %s: current=%d, max=%d",
                            quotaDimension, currentUsage, maxAllowed));
        this.quotaDimension = quotaDimension;
        this.currentUsage = currentUsage;
        this.maxAllowed = maxAllowed;
    }

    /**
     * Returns the quota dimension that was exceeded.
     *
    * @return the exceeded quota dimension
     */
    public String getQuotaDimension() {
        return quotaDimension;
    }

    /**
     * Returns the current usage count.
     *
     * @return current number of active members or principals
     */
    public long getCurrentUsage() {
        return currentUsage;
    }

    /**
     * Returns the maximum allowed count.
     *
     * @return the configured hard limit
     */
    public long getMaxAllowed() {
        return maxAllowed;
    }
}
