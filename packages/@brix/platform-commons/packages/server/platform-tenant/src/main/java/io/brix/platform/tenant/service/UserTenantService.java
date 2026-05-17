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

import io.brix.platform.tenant.entity.Tenant;

import java.util.List;
import java.util.Optional;

/**
 * User-facing Tenant Service — provides tenant operations for authenticated users.
 *
 * <p>Differs from {@link TenantProvisioningService} (admin lifecycle) and the enterprise
 * {@code TenantManagementService} (CRUD). This service handles end-user operations:</p>
 * <ul>
 *   <li>List tenants the user has access to (via membership)</li>
 *   <li>Get the current tenant for the authenticated user</li>
 *   <li>Switch to a different tenant</li>
 * </ul>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — user-facing multi-tenant capability</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantProvisioningService
 */
public interface UserTenantService {

    /**
     * Gets all tenants accessible by the specified identity.
     *
     * <p>Resolution strategy:</p>
     * <ol>
     *   <li>Query active memberships for the identity</li>
     *   <li>For each membership, resolve the tenant entity</li>
     *   <li>Return only ACTIVE tenants</li>
     * </ol>
     *
     * <p>If the identity has no memberships (e.g., newly created via OAuth2
     * before membership is provisioned), falls back to returning the tenant
     * identified by {@code currentTenantId} from the JWT claims.</p>
     *
     * @param identityId      the identity ID (from JWT subject or user mapping)
     * @param currentTenantId the current tenant ID from JWT claims (fallback)
     * @return list of accessible active tenants, never empty
     */
    List<Tenant> getAvailableTenants(String identityId, String currentTenantId);

    /**
     * Gets a specific tenant by ID.
     *
     * @param tenantId the tenant ID (as string, will be parsed to Long)
     * @return the tenant, or empty if not found
     */
    Optional<Tenant> getTenantById(String tenantId);

    /**
     * Validates that the specified identity can access the target tenant,
     * and returns the tenant if access is granted.
     *
     * @param identityId     the identity ID requesting the switch
     * @param targetTenantId the target tenant ID
     * @param currentTenantId the current tenant ID (for audit)
     * @return the target tenant
     * @throws IllegalArgumentException if tenant not found
     * @throws SecurityException if the identity has no access to the target tenant
     */
    Tenant switchTenant(String identityId, String targetTenantId, String currentTenantId);
}
