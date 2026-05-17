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

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.dto.EffectiveConfigDto;
import io.brix.platform.tenant.dto.TenantBrandingDto;
import io.brix.platform.tenant.dto.TenantSettingsDto;
import io.brix.platform.tenant.service.TenantSettingsService;
import jakarta.validation.Valid;

/**
 * Tenant Settings REST Controller.
 *
 * <p>Provides APIs for tenant administrators to manage tenant-level
 * settings, effective configuration, and branding.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — REST endpoint for tenant configuration.</p>
 *
 * <h3>API Endpoints</h3>
 * <ul>
 *   <li>GET    /api/v1/tenant/settings           — Get tenant settings</li>
 *   <li>PATCH  /api/v1/tenant/settings           — Update tenant settings</li>
 *   <li>GET    /api/v1/tenant/settings/effective  — Get three-layer merged config</li>
 *   <li>GET    /api/v1/tenant/branding            — Get branding</li>
 *   <li>PUT    /api/v1/tenant/branding            — Update branding</li>
 *   <li>POST   /api/v1/tenant/branding/logo       — Upload logo (≤2MB, PNG/JPG/SVG)</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@RestController
@RequestMapping("/api/v1/tenant")
public class TenantSettingsController {

    private static final Logger log = LoggerFactory.getLogger(TenantSettingsController.class);

    private final TenantSettingsService settingsService;

    public TenantSettingsController(TenantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Gets the current tenant's settings.
     */
    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        Long tenantId = requireTenantId();
        TenantSettingsDto settings = settingsService.getTenantSettings(tenantId);
        return ResponseEntity.ok(settings);
    }

    /**
     * Partially updates tenant settings (PATCH semantics).
     */
    @PatchMapping("/settings")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody TenantSettingsDto dto) {
        Long tenantId = requireTenantId();
        settingsService.updateTenantSettings(tenantId, dto);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Gets the effective (three-layer merged) configuration for the current user.
     */
    @GetMapping("/settings/effective")
    public ResponseEntity<?> getEffectiveConfig() {
        Long tenantId = requireTenantId();
        Long userId = resolveUserId();
        EffectiveConfigDto effective = settingsService.getEffectiveConfig(tenantId, userId);
        return ResponseEntity.ok(effective);
    }

    /**
     * Gets tenant branding configuration.
     */
    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        Long tenantId = requireTenantId();
        TenantBrandingDto branding = settingsService.getBranding(tenantId);
        return ResponseEntity.ok(branding);
    }

    /**
     * Updates tenant branding configuration.
     */
    @PutMapping("/branding")
    public ResponseEntity<?> updateBranding(@Valid @RequestBody TenantBrandingDto dto) {
        Long tenantId = requireTenantId();
        settingsService.updateBranding(tenantId, dto);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Uploads a tenant logo image.
     *
     * <p>Accepts PNG, JPEG, and SVG files up to 2 MB. The uploaded file's MIME type
     * is validated against both the Content-Type header and file magic bytes to
     * prevent malicious file uploads.</p>
     *
     * <p>On success, the logo URL is persisted to {@code sys_tenant.logo_url} and
     * returned in the response. The actual file storage is delegated to the
     * branding service (which may use local filesystem, S3, or CDN).</p>
     *
     * @param file the logo image file (multipart upload)
     * @return the persisted logo URL
     */
    @PostMapping(value = "/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file) {
        Long tenantId = requireTenantId();

        // --- Size validation: max 2 MB ---
        long maxSize = 2L * 1024 * 1024;
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        }
        if (file.getSize() > maxSize) {
            return ResponseEntity.badRequest().body(Map.of("message", "File exceeds 2 MB limit"));
        }

        // --- MIME type validation ---
        String contentType = file.getContentType();
        Set<String> allowedTypes = Set.of("image/png", "image/jpeg", "image/svg+xml");
        if (contentType == null || !allowedTypes.contains(contentType)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Unsupported file type. Allowed: PNG, JPG, SVG"));
        }

        // --- Magic bytes validation (PNG/JPEG) ---
        try {
            byte[] header = new byte[8];
            int bytesRead = file.getInputStream().read(header);
            if (bytesRead >= 4 && !isValidImageMagic(header, contentType)) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "File content does not match declared type"));
            }
        } catch (IOException e) {
            log.error("[Branding] Failed to read uploaded file for validation", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("message", "Failed to validate uploaded file"));
        }

        String logoUrl = settingsService.uploadLogo(tenantId, file);
        return ResponseEntity.ok(Map.of("success", true, "logoUrl", logoUrl));
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private Long requireTenantId() {
        return TenantContext.getTenantId()
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("Tenant context not available"));
    }

    private Long resolveUserId() {
        return TenantContext.getUserId()
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * Validates file magic bytes against the declared content type.
     *
     * <p>PNG: starts with 0x89 0x50 0x4E 0x47.
     * JPEG: starts with 0xFF 0xD8 0xFF.
     * SVG: content-type validation only (text-based format).
     *
     * @param header first bytes of the file
     * @param contentType declared MIME type
     * @return true if magic bytes match the declared type
     */
    private boolean isValidImageMagic(byte[] header, String contentType) {
        if ("image/png".equals(contentType)) {
            return header.length >= 4
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47;
        }
        if ("image/jpeg".equals(contentType)) {
            return header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }
        // SVG is text-based; magic bytes check not applicable
        return true;
    }
}
