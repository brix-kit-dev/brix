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
import org.springframework.util.Assert;

import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.CreateTenantRequest;
import io.brix.platform.tenant.entity.InstallationQuota;
import io.brix.platform.tenant.entity.Organization;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.exception.QuotaExceededException;
import io.brix.platform.tenant.repository.BizUserProfileRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.OrganizationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Implementation of {@link TenantProvisioningService} for tenant lifecycle management.
 *
 * <p>This service implements the complete tenant provisioning workflow, ensuring
 * atomic creation of the tenant directory row and tenant-owned defaults.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer - Platform Commons Service Implementation</p>
 *
 * <h3>Transaction Management</h3>
 * <p>All public methods are transactional with REQUIRED propagation.
 * Failed operations roll back completely, ensuring no partial data exists.
 *
 * <h3>Provisioning Workflow</h3>
 * <p>The {@link #createTenant} method performs the following steps atomically:
 * <pre>
 * createTenant()
 *     │
 *     ├─► 1. Validate request (code uniqueness)
 *     ├─► 2. INSERT sys_tenant (status = PENDING_ACTIVATION)
 *     └─► 3. INSERT sys_organization (root organization)
 * </pre>
 *
 * <h3>ID Generation Strategy</h3>
 * <p>All entity IDs are generated using the Snowflake algorithm via
 * {@link IdGenerator}, ensuring:
 * <ul>
 *   <li>Global uniqueness across distributed deployments</li>
 *   <li>Time-ordered IDs for efficient indexing</li>
 *   <li>No database sequence contention</li>
 * </ul>
 *
 * <h3>Default Organization</h3>
 * <p>Each tenant is created with a default root organization:
 * <ul>
 *   <li>Code: "default"</li>
 *   <li>Name: Same as tenant name</li>
 *   <li>Parent: null (root level)</li>
 *   <li>Status: ACTIVE</li>
 * </ul>
 *
 * <h3>Audit Logging</h3>
 * <p>Note: Audit logging integration is handled separately through
 * {@link AuditService}. Callers should log provisioning events explicitly.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantProvisioningService
 * @see IdGenerator
 */
@Service
public class TenantProvisioningServiceImpl implements TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningServiceImpl.class);

    /**
     * Default organization code for newly created tenants.
     * Each tenant gets a root organization with this code.
     */
    private static final String DEFAULT_ORG_CODE = "default";

    /**
     * Default organization type for root organizations.
     */
    private static final String DEFAULT_ORG_TYPE = "ROOT";

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final InstallationQuotaRepository installationQuotaRepository;
    private final OrganizationRepository organizationRepository;
    private final BizUserProfileRepository bizUserProfileRepository;
    private final IdentityRepository identityRepository;
    private final IdGenerator idGenerator;

    /**
     * Constructs a new TenantProvisioningServiceImpl with required dependencies.
     *
     * <p>All dependencies are injected by Spring IoC container.
     * Each repository handles its specific entity type, while the
     * IdGenerator provides Snowflake-based ID generation.
     *
     * @param tenantRepository repository for tenant operations
     * @param tenantMemberRepository repository for tenant member operations
     * @param organizationRepository repository for organization operations
     * @param identityRepository repository for identity validation
     * @param idGenerator generator for Snowflake IDs
     */
    public TenantProvisioningServiceImpl(
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            InstallationQuotaRepository installationQuotaRepository,
            OrganizationRepository organizationRepository,
            BizUserProfileRepository bizUserProfileRepository,
            IdentityRepository identityRepository,
            IdGenerator idGenerator) {
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.installationQuotaRepository = installationQuotaRepository;
        this.organizationRepository = organizationRepository;
        this.bizUserProfileRepository = bizUserProfileRepository;
        this.identityRepository = identityRepository;
        this.idGenerator = idGenerator;
    }

    /**
     * Creates a new pending tenant.
     *
     * <h4>Implementation Details</h4>
     * <ol>
     *   <li><b>Validation Phase:</b>
     *     <ul>
     *       <li>Validates request is not null</li>
     *       <li>Ensures tenant code is unique</li>
     *     </ul>
     *   </li>
     *   <li><b>Tenant Creation Phase:</b>
     *     <ul>
     *       <li>Generates Snowflake ID for tenant</li>
     *       <li>Creates tenant with PENDING_ACTIVATION status</li>
     *       <li>Persists tenant entity</li>
     *     </ul>
     *   </li>
     *   <li><b>Organization Creation Phase:</b>
     *     <ul>
     *       <li>Generates Snowflake ID for organization</li>
     *       <li>Creates root organization with tenant name</li>
     *       <li>Persists organization entity</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <h4>Failure Handling</h4>
     * <p>Any exception during provisioning triggers complete rollback.
     * The caller should handle exceptions appropriately:
     * <ul>
     *   <li>{@code IllegalStateException} - Duplicate tenant code</li>
     *   <li>{@code DataAccessException} - Database errors</li>
     * </ul>
     *
     * @param request the tenant creation request
     * @return the created tenant entity
     */
    @Override
    @Transactional
        @CrossTenantAccess(
            reason = "Platform tenant provisioning creates only the pending tenant boundary and root organization before a tenant-scoped context exists. FIRST_OWNER/Profile creation is deferred to the invitation acceptance transaction.",
            approval = "BRX-TENANT-OWNER-004-PHASE3")
    public Tenant createTenant(CreateTenantRequest request) {
        // =====================================================================
        // Phase 1: Request Validation
        // =====================================================================
        Assert.notNull(request, "CreateTenantRequest cannot be null");
        Assert.hasText(request.getCode(), "Tenant code cannot be empty");
        Assert.hasText(request.getName(), "Tenant name cannot be empty");
        if (request.getOwnerIdentityId() != null) {
            throw new IllegalArgumentException(
                "Tenant creation no longer accepts ownerIdentityId; use FIRST_OWNER invitation");
        }

        log.info("Starting pending tenant provisioning: code={}, name={}",
            request.getCode(), request.getName());

        // Validate tenant code uniqueness
        if (tenantRepository.existsByCode(request.getCode())) {
            log.warn("Tenant code already exists: {}", request.getCode());
            throw new IllegalStateException("Tenant code already exists: " + request.getCode());
        }

        // =====================================================================
        // Phase 2: Create Tenant Entity
        // =====================================================================
        Tenant tenant = new Tenant(request.getCode(), request.getName());
        tenant.setId(idGenerator.nextId());
        tenant.setStatus(TenantStatus.PENDING_ACTIVATION);
        
        tenant = tenantRepository.save(tenant);
        log.debug("Created tenant: id={}, code={}", tenant.getId(), tenant.getCode());

        // =====================================================================
        // Phase 3: Create Default Organization
        // Every tenant starts with a root organization named after the tenant.
        // This provides the initial organizational structure for the tenant.
        // =====================================================================
        Organization defaultOrg = new Organization(tenant.getId(), DEFAULT_ORG_CODE, request.getName());
        defaultOrg.setId(idGenerator.nextId());
        defaultOrg.setOrgType(DEFAULT_ORG_TYPE);
        defaultOrg.setDescription("Default root organization for " + request.getName());
        
        organizationRepository.save(defaultOrg);
        log.debug("Created default organization: id={}, tenantId={}, code={}",
                defaultOrg.getId(), tenant.getId(), DEFAULT_ORG_CODE);

        log.info("Tenant provisioning completed successfully: tenantId={}, code={}",
                tenant.getId(), tenant.getCode());

        return tenant;
    }

    /**
     * Suspends an active tenant.
     *
     * <h4>Implementation Details</h4>
     * <p>This method:
     * <ol>
     *   <li>Validates the tenant ID is not null</li>
     *   <li>Loads the tenant entity from database</li>
     *   <li>Delegates to {@link Tenant#suspend()} for state transition</li>
     *   <li>Persists the updated tenant</li>
     * </ol>
     *
     * <h4>State Transition</h4>
     * <p>The {@code suspend()} method on Tenant entity enforces that only
     * ACTIVE tenants can be suspended. Other states will throw exception.
     *
     * @param tenantId the ID of the tenant to suspend
     */
    @Override
    @Transactional
    public void suspendTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        log.info("Suspending tenant: id={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        TenantStatus previousStatus = tenant.getStatus();

        // Delegate state transition to entity - enforces business rules
        tenant.suspend();

        if (previousStatus == TenantStatus.ACTIVE) {
            releaseInstallationTenantSlot();
        }

        tenantRepository.save(tenant);
        log.info("Tenant suspended successfully: id={}, code={}", tenantId, tenant.getCode());
    }

    /**
     * Activates a tenant.
     *
     * <h4>Implementation Details</h4>
     * <p>This method:
     * <ol>
     *   <li>Validates the tenant ID is not null</li>
     *   <li>Loads the tenant entity from database</li>
     *   <li>Delegates to {@link Tenant#activate()} for state transition</li>
     *   <li>Persists the updated tenant</li>
     * </ol>
     *
     * <h4>State Transition</h4>
     * <p>The {@code activate()} method on Tenant entity enforces that only
     * PENDING_ACTIVATION or SUSPENDED tenants can be activated.
     * TERMINATED tenants cannot be reactivated.
     *
     * @param tenantId the ID of the tenant to activate
     */
    @Override
    @Transactional
    public void activateTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        log.info("Activating tenant: id={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        TenantStatus previousStatus = tenant.getStatus();

        if (!tenant.canBeActivated()) {
            tenant.activate();
        }

        if (previousStatus != TenantStatus.ACTIVE) {
            reserveInstallationTenantSlot();
        }

        // Delegate state transition to entity - enforces business rules
        tenant.activate();

        tenantRepository.save(tenant);
        log.info("Tenant activated successfully: id={}, code={}", tenantId, tenant.getCode());
    }

    @Override
    @Transactional
    public void terminateTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        log.info("Terminating tenant: id={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        TenantStatus previousStatus = tenant.getStatus();
        tenant.terminate();

        if (previousStatus == TenantStatus.ACTIVE) {
            releaseInstallationTenantSlot();
        }

        tenantRepository.save(tenant);
        log.info("Tenant terminated successfully: id={}, code={}", tenantId, tenant.getCode());
    }

    private void reserveInstallationTenantSlot() {
        InstallationQuota quota = lockInstallationQuota();
        if (!quota.hasAvailableSlot()) {
            throw new QuotaExceededException("installationTenants", quota.getUsed(), quota.getQuota());
        }
        quota.reserveSlot();
        installationQuotaRepository.save(quota);
    }

    private void releaseInstallationTenantSlot() {
        InstallationQuota quota = lockInstallationQuota();
        quota.releaseSlot();
        installationQuotaRepository.save(quota);
    }

    private InstallationQuota lockInstallationQuota() {
        return installationQuotaRepository
            .findByInstallationIdForUpdate(InstallationQuota.DEFAULT_INSTALLATION_ID)
            .orElseGet(() -> installationQuotaRepository.saveAndFlush(new InstallationQuota(
                InstallationQuota.DEFAULT_INSTALLATION_ID,
                InstallationQuota.DEFAULT_TENANT_QUOTA,
                0
            )));
    }
}
