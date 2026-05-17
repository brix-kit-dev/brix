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

import io.brix.platform.common.dto.ApiResponse;
import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.service.UserTenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User-facing Tenant REST Controller.
 *
 * <p>Provides REST API endpoints for authenticated users to query and manage
 * their tenant context. This controller is the backend counterpart of the
 * frontend {@code TenantRepository.ts} (platform-tenant-web).</p>
 *
 * <h3>API Contract Alignment</h3>
 * <p>The endpoints and response formats align with the frontend
 * {@code TenantRepository} class which uses baseUrl = {@code /api/v1/tenant}.</p>
 *
 * <h3>API Endpoints</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/tenant/available}           — List available tenants for current user</li>
 *   <li>{@code GET  /api/v1/tenant/current}             — Get current tenant info</li>
 *   <li>{@code GET  /api/v1/tenant/{tenantId}}          — Get tenant by ID</li>
 *   <li>{@code POST /api/v1/tenant/switch}              — Switch to a different tenant</li>
 *   <li>{@code GET  /api/v1/tenant/{tenantId}/features} — Get tenant feature flags</li>
 * </ul>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — user-facing multi-tenant REST endpoint.
 * Auto-discovered by {@code TenantAutoConfiguration} via
 * {@code @ComponentScan(basePackages = "io.brix.platform.tenant.controller")}.</p>
 *
 * <h3>Security</h3>
 * <p>All endpoints require an authenticated request (JWT Bearer token).
 * Tenant context is set by {@code TenantFilter} before reaching this controller.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see UserTenantService
 */
@RestController
@RequestMapping("/api/v1/tenant")
public class UserTenantController {

    private static final Logger log = LoggerFactory.getLogger(UserTenantController.class);

    private final UserTenantService userTenantService;

    public UserTenantController(UserTenantService userTenantService) {
        this.userTenantService = userTenantService;
    }

    /**
     * Get all tenants available to the current authenticated user.
     *
     * <p>Frontend: {@code TenantRepository.getAvailableTenants()}</p>
     * <p>Response format: {@code ApiResponse<TenantListResponse>}</p>
     *
     * @return list of available tenants wrapped in ApiResponse
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAvailableTenants() {
        String currentTenantId = TenantContext.getTenantId().orElse(null);
        String userId = TenantContext.getUserId().orElse(null);

        log.debug("Getting available tenants: userId={}, currentTenantId={}", userId, currentTenantId);

        List<Tenant> tenants = userTenantService.getAvailableTenants(userId, currentTenantId);

        List<Map<String, Object>> tenantDtos = tenants.stream()
                .map(this::toTenantDto)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenants", tenantDtos);
        data.put("total", tenantDtos.size());

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * Get the current tenant for the authenticated user.
     *
     * <p>Frontend: {@code TenantRepository.getCurrentTenant()}</p>
     *
     * @return current tenant info wrapped in ApiResponse
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentTenant() {
        String currentTenantId = TenantContext.getTenantId().orElse(null);

        if (currentTenantId == null) {
            log.debug("No tenant context available");
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        return userTenantService.getTenantById(currentTenantId)
                .map(tenant -> ResponseEntity.ok(ApiResponse.success(toTenantDto(tenant))))
                .orElseGet(() -> {
                    // Tenant ID exists in JWT but not in database — return minimal info
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", currentTenantId);
                    dto.put("code", currentTenantId);
                    dto.put("name", currentTenantId);
                    dto.put("status", "ACTIVE");
                    return ResponseEntity.ok(ApiResponse.success(dto));
                });
    }

    /**
     * Get tenant by ID.
     *
     * <p>Frontend: {@code TenantRepository.getTenant(tenantId)}</p>
     *
     * @param tenantId the tenant ID or code
     * @return tenant info wrapped in ApiResponse
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenant(
            @PathVariable String tenantId) {
        return userTenantService.getTenantById(tenantId)
                .map(tenant -> ResponseEntity.ok(ApiResponse.success(toTenantDto(tenant))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Switch to a different tenant.
     *
     * <p>Frontend: {@code TenantRepository.switchTenant(tenantId)}</p>
     *
     * @param request containing the target tenantId
     * @return the new tenant info wrapped in ApiResponse
     */
    @PostMapping("/switch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> switchTenant(
            @RequestBody SwitchTenantRequest request) {
        String userId = TenantContext.getUserId().orElse(null);
        String currentTenantId = TenantContext.getTenantId().orElse(null);

        Tenant tenant = userTenantService.switchTenant(userId, request.tenantId(), currentTenantId);

        return ResponseEntity.ok(ApiResponse.success(toTenantDto(tenant)));
    }

    /**
     * Get feature flags for a specific tenant.
     *
     * <p>Frontend: {@code TenantRepository.getTenantFeatures(tenantId)}</p>
     *
     * <p>Phase 1: Returns an empty feature list. Feature flags will be
     * implemented when the feature management module is available.</p>
     *
     * @param tenantId the tenant ID
     * @return list of features wrapped in ApiResponse
     */
    @GetMapping("/{tenantId}/features")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantFeatures(
            @PathVariable String tenantId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("features", List.of());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ==================== Request/Response DTOs ====================

    /**
     * Request DTO for tenant switch operation.
     */
    public record SwitchTenantRequest(String tenantId) {}

    // ==================== Private Helpers ====================

    /**
     * Converts a Tenant entity to the DTO format expected by the frontend.
     *
     * <p>Aligns with the frontend {@code Tenant} interface in TenantContext.ts:
     * {@code { id: string, code: string, name: string, status: string }}</p>
     */
    private Map<String, Object> toTenantDto(Tenant tenant) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", String.valueOf(tenant.getId()));
        dto.put("code", tenant.getCode());
        dto.put("name", tenant.getName());
        dto.put("status", tenant.getStatus().name());
        if (tenant.getCreatedAt() != null) {
            dto.put("createdAt", tenant.getCreatedAt().toString());
        }
        if (tenant.getUpdatedAt() != null) {
            dto.put("updatedAt", tenant.getUpdatedAt().toString());
        }
        return dto;
    }
}
