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

import io.brix.platform.tenant.dto.CreateTenantRequest;
import io.brix.platform.tenant.entity.Tenant;

/**
 * Service interface for tenant provisioning operations.
 *
 * <p>This service provides the contract for tenant lifecycle management,
 * including creation, suspension, and activation. It encapsulates the
 * complete tenant provisioning workflow ensuring data consistency.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer - Platform Commons Capability Implementation</p>
 *
 * <h3>Design Rationale</h3>
 * <p>Tenant provisioning is a critical operation that must:
 * <ul>
 *   <li>Create multiple related entities atomically (tenant, member, organization)</li>
 *   <li>Ensure exactly one OWNER member per tenant</li>
 *   <li>Create default organizational structure</li>
 *   <li>Maintain audit trail for compliance</li>
 * </ul>
 *
 * <h3>Transaction Semantics</h3>
 * <p>All operations in this service are transactional:
 * <ul>
 *   <li>{@link #createTenant} - Creates tenant with all related entities atomically</li>
 *   <li>{@link #suspendTenant} - Updates tenant status within transaction</li>
 *   <li>{@link #activateTenant} - Updates tenant status within transaction</li>
 *   <li>{@link #terminateTenant} - Terminates tenant within transaction</li>
 * </ul>
 *
 * <h3>State Transitions</h3>
 * <pre>
 *     ┌──────────────────┐
 *     │ PENDING_ACTIVATION │
 *     └─────────┬────────┘
 *               │ activate()
 *               ▼
 *     ┌──────────────────┐
 *     │      ACTIVE      │ ◄────────┐
 *     └─────────┬────────┘          │
 *               │ suspend()         │ activate()
 *               ▼                   │
 *     ┌──────────────────┐          │
 *     │    SUSPENDED     │ ─────────┘
 *     └──────────────────┘
 * </pre>
 *
 * <h3>MVP Scope Boundaries</h3>
 * <p>The following features are explicitly OUT OF SCOPE for MVP:
 * <ul>
 *   <li>REST API endpoints for tenant management (deferred to commercial version)</li>
 *   <li>Tenant deletion or soft-delete functionality</li>
 *   <li>Member invitation workflows</li>
 *   <li>Tenant configuration management</li>
 *   <li>Async event publishing (e.g., Kafka)</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Service
 * public class TenantOnboardingService {
 *     private final TenantProvisioningService provisioningService;
 *
 *     public void onboardNewCustomer(CustomerDTO customer) {
 *         CreateTenantRequest request = CreateTenantRequest.builder()
 *             .code(customer.getCompanyCode())
 *             .name(customer.getCompanyName())
 *             .ownerIdentityId(customer.getAdminUserId())
 *             .build();
 *
 *         Tenant tenant = provisioningService.createTenant(request);
 *         // Continue with additional onboarding steps...
 *     }
 * }
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see CreateTenantRequest
 * @see Tenant
 * @see io.brix.platform.tenant.enums.TenantStatus
 */
public interface TenantProvisioningService {

    /**
     * Creates a new tenant with complete provisioning workflow.
     *
     * <p>This method performs the complete tenant provisioning workflow:
     * <ol>
     *   <li>Validates the request (code uniqueness, owner identity existence)</li>
     *   <li>Creates the sys_tenant record with PENDING_ACTIVATION status</li>
     *   <li>Creates the sys_tenant_member record with OWNER type for the specified identity</li>
     *   <li>Creates the default sys_organization (root organization for the tenant)</li>
     * </ol>
     *
     * <h4>Transaction Guarantee</h4>
     * <p>All operations are performed within a single transaction. If any step fails,
     * the entire operation is rolled back, ensuring no partial tenant data exists.
     *
     * <h4>ID Generation</h4>
     * <p>All entity IDs are generated using the Snowflake algorithm, ensuring
     * globally unique identifiers suitable for distributed deployments.
     *
     * <h4>Validation Rules</h4>
     * <ul>
     *   <li>code: Must be unique, non-empty, max 64 characters</li>
     *   <li>name: Must be non-empty, max 256 characters</li>
     *   <li>ownerIdentityId: Must reference an existing identity in sys_identity</li>
     * </ul>
     *
     * @param request the tenant creation request containing code, name, and owner identity
     * @return the created tenant entity with generated ID
     * @throws IllegalArgumentException if request validation fails
     * @throws IllegalStateException if tenant code already exists
     * @throws io.brix.platform.tenant.exception.InvalidReferenceException if owner identity not found
     */
    Tenant createTenant(CreateTenantRequest request);

    /**
     * Suspends an active tenant.
     *
     * <p>Suspension temporarily disables tenant access while preserving all data.
     * A suspended tenant can be reactivated using {@link #activateTenant}.
     *
     * <h4>Effects of Suspension</h4>
     * <ul>
     *   <li>User logins for this tenant are blocked</li>
     *   <li>API access returns 403 Forbidden</li>
     *   <li>Data remains intact (read-only for platform admins)</li>
     *   <li>Audit logs continue to be preserved</li>
     * </ul>
     *
     * <h4>Valid State Transitions</h4>
     * <p>Suspension is only valid from ACTIVE status. Attempting to suspend
     * a tenant in PENDING_ACTIVATION, SUSPENDED, or TERMINATED status will
     * throw an exception.
     *
     * @param tenantId the ID of the tenant to suspend
     * @throws IllegalArgumentException if tenantId is null
     * @throws jakarta.persistence.EntityNotFoundException if tenant not found
     * @throws IllegalStateException if tenant is not in ACTIVE status
     */
    void suspendTenant(Long tenantId);

    /**
     * Activates a tenant.
     *
     * <p>Activation enables full tenant access. This method can be used to:
     * <ul>
     *   <li>Activate a newly created tenant (from PENDING_ACTIVATION)</li>
     *   <li>Reactivate a suspended tenant (from SUSPENDED)</li>
     * </ul>
     *
     * <h4>Effects of Activation</h4>
     * <ul>
     *   <li>User logins for this tenant are enabled</li>
     *   <li>Full API access is restored</li>
     *   <li>All tenant operations resume normally</li>
     * </ul>
     *
     * <h4>Valid State Transitions</h4>
     * <p>Activation is valid from PENDING_ACTIVATION or SUSPENDED status.
     * Attempting to activate a TERMINATED tenant will throw an exception.
     *
     * @param tenantId the ID of the tenant to activate
     * @throws IllegalArgumentException if tenantId is null
     * @throws jakarta.persistence.EntityNotFoundException if tenant not found
     * @throws IllegalStateException if tenant cannot be activated (e.g., TERMINATED)
     */
    void activateTenant(Long tenantId);

    /**
     * Terminates a tenant.
     *
     * <p>Termination is irreversible. If the tenant was active, the
     * installation-level active tenant quota usage is released in the same
     * transaction.</p>
     *
     * @param tenantId the ID of the tenant to terminate
     * @throws IllegalArgumentException if tenantId is null
     * @throws jakarta.persistence.EntityNotFoundException if tenant not found
     */
    void terminateTenant(Long tenantId);
}
