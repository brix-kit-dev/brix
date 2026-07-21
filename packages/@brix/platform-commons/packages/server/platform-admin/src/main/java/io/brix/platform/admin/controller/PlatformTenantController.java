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
package io.brix.platform.admin.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.admin.controller.PlatformAuditController.PageResponse;
import io.brix.platform.admin.dto.CreatePlatformTenantRequest;
import io.brix.platform.admin.dto.PlatformTenantDto;
import io.brix.platform.admin.dto.UpdateTenantStatusRequest;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.annotation.RequirePermission;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.service.AuditService;
import io.brix.platform.tenant.service.TenantProvisioningService;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.TenantProvisioningCapability;
import io.runtime.sdk.capability.TenantProvisioningCapability.CreateTenantCommand;
import io.runtime.sdk.capability.TenantProvisioningCapability.TenantRecord;
import io.runtime.sdk.capability.TenantQuotaCapability;
import io.runtime.sdk.capability.TenantQuotaCapability.InstallationQuotaSnapshot;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

/**
 * Platform tenant lifecycle management endpoints.
 *
 * <h3>Route Prefix</h3>
 * <p>{@code /api/platform/tenants}
 *
 * <h3>Design</h3>
 * <p>This controller provides read + lifecycle-state-change operations over
 * {@code sys_tenant}. It operates directly on {@code TenantRepository} (Layer 2C)
 * because no separate tenant service exposes the lifecycle FSM yet. All mutations
 * are audit-logged via {@code AuditService}.
 *
 * <h3>Tenant Lifecycle FSM (SSOT §7)</h3>
 * <pre>
 * PENDING_ACTIVATION → ACTIVE
 * ACTIVE             → SUSPENDED
 * SUSPENDED          → ACTIVE
 * Any state          → TERMINATED  (irreversible — not enforced here; business decision)
 * </pre>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@RestController
@RequestMapping("/api/platform/tenants")
public class PlatformTenantController {

    private static final Logger log = LoggerFactory.getLogger(PlatformTenantController.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final TenantRepository tenantRepository;
    private final AuditService auditService;
    private final TenantProvisioningService tenantProvisioningService;
    private final SecurityContextHolder securityContextHolder;
    private final TenantQuotaCapability tenantQuotaCapability;
    private final TenantProvisioningCapability tenantProvisioningCapability;

    public PlatformTenantController(
            TenantRepository tenantRepository,
            AuditService auditService,
            TenantProvisioningService tenantProvisioningService,
            SecurityContextHolder securityContextHolder,
            TenantQuotaCapability tenantQuotaCapability,
            TenantProvisioningCapability tenantProvisioningCapability) {
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.tenantProvisioningService = tenantProvisioningService;
        this.securityContextHolder = securityContextHolder;
        this.tenantQuotaCapability = tenantQuotaCapability;
        this.tenantProvisioningCapability = tenantProvisioningCapability;
    }

    // ========================================================================
    // GET /api/platform/tenants  — list tenants
    // ========================================================================

    /**
     * Lists all tenants visible from the platform perspective.
     *
     * @param page   0-based page index (default 0)
     * @param size   page size, capped at 100 (default 20)
     * @param status optional status filter (PENDING_ACTIVATION / ACTIVE / SUSPENDED / TERMINATED)
     * @return paginated list of tenant DTOs
     */
    @GetMapping
    @RequirePermission(PlatformPermissions.TENANT_READ)
    public ResponseEntity<PageResponse<PlatformTenantDto>> listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Tenant> result;
        if (status != null && !status.isBlank()) {
            TenantStatus tenantStatus = parseTenantStatus(status);
            result = tenantRepository.findByStatus(tenantStatus, pageable);
        } else {
            result = tenantRepository.findAll(pageable);
        }

        InstallationQuotaSnapshot quota = tenantQuotaCapability.getInstallationQuota();
        List<PlatformTenantDto> content = result.stream().map(tenant -> toDto(tenant, quota)).toList();
        return ResponseEntity.ok(new PageResponse<>(content, safePage, safeSize, result.getTotalElements()));
    }

    // ========================================================================
    // POST /api/platform/tenants  — create tenant
    // ========================================================================

    /**
     * Creates a new tenant in {@code PENDING_ACTIVATION} status.
     *
     * <p>The tenant can be activated afterwards via
     * {@code PATCH /api/platform/tenants/{id}/status}.
     *
     * @param request tenant code and display name
     * @return 201 Created with the newly created tenant DTO
     */
    @PostMapping
    @RequirePermission(PlatformPermissions.TENANT_CREATE)
    public ResponseEntity<PlatformTenantDto> createTenant(
            @Valid @RequestBody CreatePlatformTenantRequest request) {

        long operatorId = requireIdentityId();
        tenantQuotaCapability.requireCanCreateTenant();

        if (tenantRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Tenant code already exists: " + request.code());
        }

        TenantRecord tenant = tenantProvisioningCapability.createTenant(
            new CreateTenantCommand(request.code(), request.name(), operatorId));
        Long createdTenantId = tenant.id();
        if (createdTenantId == null) {
            throw new IllegalStateException("Tenant provisioning returned an empty tenant id");
        }
        Tenant createdTenant = tenantRepository.findById(createdTenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found after creation: " + createdTenantId));

        auditService.log(AuditEvent.builder()
                .createdBy(operatorId)
                .action(AuditAction.TENANT_CREATED)
                .resourceType("TENANT")
                .resourceId(String.valueOf(tenant.id()))
                .description("Tenant created by platform admin")
                .success(true)
                .build());

        log.info("Tenant created: id={}, code={}, operatorId={}",
                tenant.id(), tenant.code(), operatorId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(createdTenant));
    }

    // ========================================================================
    // PATCH /api/platform/tenants/{id}/status  — change tenant lifecycle status
    // ========================================================================

    /**
     * Changes a tenant's lifecycle status.
     *
     * <p>The transition is persisted immediately. An audit event is emitted
     * with the reason provided by the operator.
     *
     * <p><b>R-10:</b> {@code reason} must not contain secrets.
     *
     * @param tenantId target tenant ID
     * @param request  target status + reason
     * @return 200 with updated tenant DTO
     */
    @PatchMapping("/{id}/status")
    @RequirePermission(PlatformPermissions.TENANT_UPDATE_STATUS)
    public ResponseEntity<PlatformTenantDto> updateTenantStatus(
            @PathVariable("id") Long tenantId,
            @Valid @RequestBody UpdateTenantStatusRequest request) {

        long operatorId = requireIdentityId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        TenantStatus previousStatus = tenant.getStatus();
        TenantStatus targetStatus = parseTenantStatus(request.status());
        if (previousStatus != targetStatus) {
            switch (targetStatus) {
            case ACTIVE -> tenantProvisioningService.activateTenant(tenantId);
            case SUSPENDED -> tenantProvisioningService.suspendTenant(tenantId);
            case TERMINATED -> tenantProvisioningService.terminateTenant(tenantId);
            case PENDING_ACTIVATION -> throw new IllegalStateException(
                "Cannot transition tenant back to PENDING_ACTIVATION");
            }
            tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found after status update: " + tenantId));
        }

        // Audit the status change
        auditService.log(AuditEvent.builder()
                .createdBy(operatorId)
                .action(AuditAction.TENANT_STATUS_CHANGED)
                .resourceType("TENANT")
                .resourceId(String.valueOf(tenantId))
            .description("Tenant status changed from " + previousStatus.name() + " to " + request.status()
                        + ". Reason: " + sanitize(request.reason()))
                .success(true)
                .build());

        log.info("Tenant status updated: tenantId={}, from={}, to={}, operatorId={}",
            tenantId, previousStatus.name(), request.status(), operatorId);

        return ResponseEntity.ok(toDto(tenant));
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private PlatformTenantDto toDto(Tenant tenant) {
        return toDto(tenant, tenantQuotaCapability.getInstallationQuota());
    }

    private PlatformTenantDto toDto(Tenant tenant, InstallationQuotaSnapshot quota) {
        return new PlatformTenantDto(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt(),
                quota.used(),
                quota.quota(),
                quota.licenseStatus(),
                tenant.getDefaultLocale(),
                tenant.getDefaultTimezone(),
                tenant.getDefaultTheme() != null ? tenant.getDefaultTheme().name() : null
        );
    }

    private TenantStatus parseTenantStatus(String status) {
        try {
            return TenantStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid tenant status: " + status);
        }
    }

    private long requireIdentityId() {
        String userId = securityContextHolder.getCurrentUserId().orElse(null);
        if (userId == null || userId.isBlank()) {
            throw new AuthFlowException(AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                    "Authentication required.");
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            log.warn("[PlatformTenant] Non-numeric principal: {}", userId);
            throw new AuthFlowException(AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                    "Invalid identity token (non-numeric subject).");
        }
    }

    private String sanitize(String reason) {
        return (reason != null && !reason.isBlank()) ? reason : "(no reason provided)";
    }
}
