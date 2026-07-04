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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.annotation.RequirePermission;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.exception.QuotaExceededException;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.service.TenantProvisioningService;
import jakarta.persistence.EntityNotFoundException;

/**
 * 租户管理 REST 控制器
 *
 * <p>提供平台管理员的租户 CRUD 和生命周期管理 API。
 * 与前端 TenantAdminRepository 的 API 契约对齐。</p>
 *
 * <h3>架构层</h3>
 * <p>Layer 2C: Platform Commons — 多租户能力 REST 端点</p>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>GET    /api/admin/tenants             — 租户列表（分页）</li>
 *   <li>GET    /api/admin/tenants/{id}        — 租户详情</li>
 *   <li>POST   /api/admin/tenants/{id}/activate   — 激活租户</li>
 *   <li>POST   /api/admin/tenants/{id}/suspend    — 暂停租户</li>
 *   <li>POST   /api/admin/tenants/{id}/terminate  — 终止租户</li>
 *   <li>GET    /api/admin/tenants/statistics  — 平台统计</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequirePermission(PlatformPermissions.TENANT_VIEW)
public class TenantAdminController {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminController.class);

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;

    public TenantAdminController(TenantRepository tenantRepository,
                                  TenantProvisioningService provisioningService) {
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
    }

    /**
     * 获取租户列表（分页）
     *
     * @param page     页码（从 1 开始，默认 1）
     * @param pageSize 每页大小（默认 10）
     * @param keyword  搜索关键词（可选，匹配 name）
     * @param status   状态过滤（可选）
     * @param sortBy   排序字段（默认 createdAt）
     * @param sortOrder 排序方向（默认 desc）
     * @return 分页租户列表
     */
    @GetMapping
    public ResponseEntity<?> getTenants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            // Map frontend sortBy names to entity field names
            String sortField = switch (sortBy) {
                case "name" -> "name";
                case "memberCount" -> "createdAt"; // fallback, memberCount not in entity
                default -> "createdAt";
            };

            Sort sort = "asc".equalsIgnoreCase(sortOrder)
                    ? Sort.by(sortField).ascending()
                    : Sort.by(sortField).descending();

            // Spring Data pages are 0-based, frontend sends 1-based
            Pageable pageable = PageRequest.of(Math.max(0, page - 1), pageSize, sort);

            Page<Tenant> tenantPage;
            if (keyword != null && !keyword.isBlank()) {
                tenantPage = tenantRepository.findByNameContainingIgnoreCase(keyword, pageable);
            } else if (status != null && !status.isBlank()) {
                TenantStatus tenantStatus = TenantStatus.valueOf(status);
                tenantPage = tenantRepository.findByStatus(tenantStatus, pageable);
            } else {
                tenantPage = tenantRepository.findAll(pageable);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("items", tenantPage.getContent().stream()
                    .map(this::toTenantDTO)
                    .toList());
            response.put("total", tenantPage.getTotalElements());
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalPages", tenantPage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[TenantAdmin] Failed to list tenants", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to retrieve tenant list"));
        }
    }

    /**
     * 获取租户详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTenant(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tenant id is required"));
        }
        return tenantRepository.findById(id)
                .map(tenant -> ResponseEntity.ok(toTenantDTO(tenant)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 激活租户
     */
    @PostMapping("/{id}/activate")
    @RequirePermission(PlatformPermissions.TENANT_MANAGE)
    public ResponseEntity<?> activateTenant(@PathVariable Long id) {
        try {
            provisioningService.activateTenant(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (QuotaExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (EntityNotFoundException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 暂停租户
     */
    @PostMapping("/{id}/suspend")
    @RequirePermission(PlatformPermissions.TENANT_MANAGE)
    public ResponseEntity<?> suspendTenant(@PathVariable Long id) {
        try {
            provisioningService.suspendTenant(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (EntityNotFoundException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 终止租户
     */
    @PostMapping("/{id}/terminate")
    @RequirePermission(PlatformPermissions.TENANT_MANAGE)
    public ResponseEntity<?> terminateTenant(@PathVariable Long id) {
        try {
            provisioningService.terminateTenant(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (EntityNotFoundException | IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 获取平台统计
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalTenants", tenantRepository.count());
            stats.put("activeTenants", tenantRepository.countByStatus(TenantStatus.ACTIVE));
            stats.put("suspendedTenants", tenantRepository.countByStatus(TenantStatus.SUSPENDED));
            stats.put("pendingTenants", tenantRepository.countByStatus(TenantStatus.PENDING_ACTIVATION));
            stats.put("totalMembers", 0); // TODO: Implement when member count query is available
            stats.put("newTenantsThisMonth", 0); // TODO: Implement with date-based query
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("[TenantAdmin] Failed to get statistics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to retrieve statistics"));
        }
    }

    /**
     * Convert Tenant entity to DTO for frontend consumption.
     * Aligns with TenantDTO defined in tenant-shared/types.ts.
     */
    private Map<String, Object> toTenantDTO(Tenant tenant) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", String.valueOf(tenant.getId()));
        dto.put("code", tenant.getCode());
        dto.put("name", tenant.getName());
        dto.put("status", tenant.getStatus().name());
        dto.put("memberCount", 0);
        dto.put("createdAt", tenant.getCreatedAt() != null ? tenant.getCreatedAt().toString() : null);
        dto.put("updatedAt", tenant.getUpdatedAt() != null ? tenant.getUpdatedAt().toString() : null);
        return dto;
    }
}
