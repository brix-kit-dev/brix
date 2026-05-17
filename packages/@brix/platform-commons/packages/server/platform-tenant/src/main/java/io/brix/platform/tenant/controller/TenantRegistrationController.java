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
package io.brix.platform.tenant.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.tenant.dto.CreateTenantRequest;
import io.brix.platform.tenant.dto.RegisterTenantRequest;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantMemberType;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.service.TenantProvisioningService;
import jakarta.validation.Valid;

/**
 * Tenant self-service registration controller — unauthenticated endpoint.
 *
 * <p>Provides the public registration endpoint for tenant self-service creation.
 * In production, the registrant's identity is verified via Identity Token
 * (phone/email + OTP) before reaching this endpoint.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — public REST endpoint for tenant registration.</p>
 *
 * <h3>Registration Flow</h3>
 * <ol>
 *   <li>Validate the registration request (code format, uniqueness, identity existence)</li>
 *   <li>Check rate limits (per-identity and global tenant limits)</li>
 *   <li>Create tenant via {@link TenantProvisioningService} (PENDING_ACTIVATION)</li>
 *   <li>Activate the tenant immediately (self-service flow)</li>
 *   <li>Return the created tenant information</li>
 * </ol>
 *
 * <h3>Rate Limiting</h3>
 * <ul>
 *   <li>Per-identity: maximum {@code brix.tenant.registration.max-per-identity} tenants
 *       owned by a single identity (default: 2)</li>
 *   <li>Global (OSS): maximum {@code brix.tenant.registration.max-global} tenants
 *       across the entire platform (default: 3 for OSS edition)</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <p>All endpoints under {@code /api/public/} are excluded from authentication
 * filters. In production, the Identity Token middleware MUST validate the
 * registrant's identity before forwarding to this endpoint. The
 * {@code ownerIdentityId} in the request body should match the {@code sub}
 * claim of the Identity Token.</p>
 *
 * <h3>API Endpoints</h3>
 * <ul>
 *   <li>POST /api/public/tenant/register — Self-service tenant registration</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@RestController
@RequestMapping("/api/public/tenant")
public class TenantRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistrationController.class);

    private final TenantProvisioningService provisioningService;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantRepository tenantRepository;

    /**
     * Maximum number of tenants a single identity can own.
     * Default: 2 (same identity max 2 tenants).
     */
    @Value("${brix.tenant.registration.max-per-identity:2}")
    private int maxTenantsPerIdentity;

    /**
     * Global maximum number of tenants for the OSS edition.
     * Default: 3. Set to 0 to disable the global limit.
     */
    @Value("${brix.tenant.registration.max-global:3}")
    private int maxGlobalTenants;

    public TenantRegistrationController(TenantProvisioningService provisioningService,
                                         TenantMemberRepository tenantMemberRepository,
                                         TenantRepository tenantRepository) {
        this.provisioningService = provisioningService;
        this.tenantMemberRepository = tenantMemberRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Registers a new tenant via self-service flow.
     *
     * <p>Creates a new tenant and assigns the registrant as the OWNER.
     * Performs rate limit checks before proceeding with tenant creation.</p>
     *
     * <p>The tenant is created with PENDING_ACTIVATION status and then
     * immediately activated for self-service registrations. The complete
     * provisioning includes: tenant record, OWNER membership, and default
     * root organization.</p>
     *
     * @param request the registration request with tenant code, name, and owner identity
     * @return 201 Created with tenant details, or error response
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        log.info("Tenant registration request: code={}, ownerIdentityId={}",
                request.getCode(), request.getOwnerIdentityId());

        // =====================================================================
        // Rate Limit Check: Per-Identity Limit
        // =====================================================================
        long ownedTenants = tenantMemberRepository
                .findByIdentityId(request.getOwnerIdentityId())
                .stream()
                .filter(m -> m.getMemberType() == TenantMemberType.OWNER)
                .count();

        if (ownedTenants >= maxTenantsPerIdentity) {
            log.warn("Identity {} has reached the tenant ownership limit ({})",
                    request.getOwnerIdentityId(), maxTenantsPerIdentity);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "code", "TENANT_LIMIT_PER_IDENTITY",
                            "message", "You have reached the maximum number of tenants (" +
                                       maxTenantsPerIdentity + ") allowed per identity"
                    ));
        }

        // =====================================================================
        // Rate Limit Check: Global Tenant Limit (OSS edition)
        // =====================================================================
        if (maxGlobalTenants > 0) {
            long totalTenants = tenantRepository.count();
            if (totalTenants >= maxGlobalTenants) {
                log.warn("Global tenant limit reached: current={}, max={}",
                        totalTenants, maxGlobalTenants);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                                "code", "TENANT_LIMIT_GLOBAL",
                                "message", "The platform has reached the maximum number of tenants (" +
                                           maxGlobalTenants + ") allowed"
                        ));
            }
        }

        // =====================================================================
        // Create Tenant via Provisioning Service
        // =====================================================================
        try {
            CreateTenantRequest createRequest = new CreateTenantRequest(
                    request.getCode(),
                    request.getName(),
                    request.getOwnerIdentityId()
            );

            Tenant tenant = provisioningService.createTenant(createRequest);

            // Self-service registration: activate immediately
            provisioningService.activateTenant(tenant.getId());
            tenant = tenantRepository.findById(tenant.getId()).orElse(tenant);

            log.info("Tenant registered successfully: id={}, code={}",
                    tenant.getId(), tenant.getCode());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("tenantId", String.valueOf(tenant.getId()));
            response.put("code", tenant.getCode());
            response.put("name", tenant.getName());
            response.put("status", tenant.getStatus().name());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalStateException e) {
            // Duplicate tenant code
            log.warn("Tenant registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "code", "TENANT_CODE_EXISTS",
                            "message", e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("Tenant registration failed unexpectedly", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "code", "REGISTRATION_FAILED",
                            "message", "Tenant registration failed"
                    ));
        }
    }
}
