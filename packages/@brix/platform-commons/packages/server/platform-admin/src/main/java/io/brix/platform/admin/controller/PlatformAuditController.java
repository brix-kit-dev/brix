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

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.admin.dto.PlatformAuditLogDto;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.annotation.RequirePermission;
import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.entity.AuditLog;
import io.brix.platform.tenant.repository.AuditLogRepository;

/**
 * Platform audit log query endpoint.
 *
 * <h3>Route Prefix</h3>
 * <p>{@code /api/platform/audit-logs}
 *
 * <h3>Design</h3>
 * <p>Audit logs are written by {@code AuditService} and read directly from
 * {@code AuditLogRepository} here. Only platform-scoped audit events are exposed
 * (i.e. rows where {@code tenant_id IS NULL}, which indicates system-level events).
 *
 * <h3>Filtering</h3>
 * <p>Supports optional filtering by:
 * <ul>
 *   <li>{@code action} — exact match on the action code</li>
 *   <li>{@code actorId} — exact match on the {@code created_by} identity</li>
 *   <li>{@code startTime} / {@code endTime} — ISO-8601 timestamps</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@RestController
@RequestMapping("/api/platform/audit-logs")
public class PlatformAuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    public PlatformAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Lists platform-level audit logs with optional filtering and pagination.
     *
     * <p>Only rows with {@code tenant_id IS NULL} are returned; tenant-scoped events
     * are excluded from this view.
     *
     * @param page      0-based page index (default 0)
     * @param size      page size, capped at 100 (default 20)
     * @param action    optional action code filter (exact match)
     * @param actorId   optional actor identity-ID filter
     * @param startTime optional ISO-8601 start timestamp (inclusive)
     * @param endTime   optional ISO-8601 end timestamp (inclusive)
     * @return paginated list of platform audit log DTOs
     */
    @GetMapping
    @RequirePermission(PlatformPermissions.AUDIT_READ)
    @CrossTenantAccess(reason = "Platform-level audit log query: super admin reads platform-scoped "
            + "audit events (tenant_id IS NULL) for security/compliance review. "
            + "Cross-tenant access is intrinsic to platform audit oversight.",
            approval = "BRIX-ARCH-3.0.9-PLATFORM-AUDIT-READ",
            readOnly = true)
    public ResponseEntity<PageResponse<PlatformAuditLogDto>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime) {

        // Cap page size to prevent abuse
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<PlatformAuditLogDto> content;
        long total;

        // Apply filters: only platform-scoped (tenant_id = null) events
        if (action != null && !action.isBlank() && startTime != null && endTime != null) {
            // Action + time range
            Page<AuditLog> result = auditLogRepository.findByTenantIdAndTimeRange(null, startTime, endTime, pageable);
            content = result.stream()
                    .filter(log -> action.equals(log.getAction()))
                    .map(this::toDto)
                    .toList();
            total = result.getTotalElements();
        } else if (action != null && !action.isBlank()) {
            // Action only
            Page<AuditLog> result = auditLogRepository.findByTenantIdAndAction(null, action, pageable);
            content = result.stream().map(this::toDto).toList();
            total = result.getTotalElements();
        } else if (actorId != null) {
            // Actor filter
            Page<AuditLog> result = auditLogRepository.findByCreatedBy(actorId, pageable);
            content = result.stream()
                    .filter(log -> log.getTenantId() == null) // platform events only
                    .map(this::toDto)
                    .toList();
            total = result.getTotalElements();
        } else if (startTime != null && endTime != null) {
            // Time range only
            Page<AuditLog> result = auditLogRepository.findByTenantIdAndTimeRange(null, startTime, endTime, pageable);
            content = result.stream().map(this::toDto).toList();
            total = result.getTotalElements();
        } else {
            // No filter — return all platform-level events
            Page<AuditLog> result = auditLogRepository.findByTenantId(null, pageable);
            content = result.stream().map(this::toDto).toList();
            total = result.getTotalElements();
        }

        return ResponseEntity.ok(new PageResponse<>(content, safePage, safeSize, total));
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private PlatformAuditLogDto toDto(AuditLog log) {
        return new PlatformAuditLogDto(
                log.getId(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getDescription(),
                log.getCreatedBy(),
                log.getClientIp(),
                log.isSuccess(),
                log.getErrorMessage(),
                log.getCreatedAt()
        );
    }

    /**
     * Generic page response wrapper.
     *
     * @param <T> element type
     */
    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements
    ) {}
}
