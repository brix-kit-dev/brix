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

import io.brix.platform.tenant.dto.TenantBrandingDto;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Public tenant REST controller — unauthenticated endpoints.
 *
 * <p>Provides public-facing tenant information that does not require
 * authentication. Used by login pages and public-facing portals to
 * render tenant branding before the user authenticates.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — public REST endpoint for tenant branding.</p>
 *
 * <h3>Security</h3>
 * <p>All endpoints under {@code /api/public/} are excluded from authentication
 * filters. Only non-sensitive, read-only data is exposed. The response includes
 * cache headers to reduce database load from unauthenticated traffic.</p>
 *
 * <h3>API Endpoints</h3>
 * <ul>
 *   <li>GET /api/public/tenant/{code}/branding — Get tenant branding by code (no auth)</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@RestController
@RequestMapping("/api/public/tenant")
public class PublicTenantController {

    private static final Logger log = LoggerFactory.getLogger(PublicTenantController.class);

    private final TenantRepository tenantRepository;

    public PublicTenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Gets tenant branding configuration by tenant code without authentication.
     *
     * <p>Designed for login pages and public portals that need to render
     * tenant-specific branding (logo, theme colors, login page text)
     * before the user has authenticated.</p>
     *
     * <p>Only returns branding for ACTIVE tenants. Returns 404 for
     * non-existent, suspended, or terminated tenants to prevent
     * information leakage about tenant lifecycle states.</p>
     *
     * @param code the unique tenant code (e.g., "acme-corp")
     * @return branding configuration or 404
     */
    @GetMapping("/{code}/branding")
    public ResponseEntity<?> getPublicBranding(@PathVariable String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Tenant code is required"));
        }

        return tenantRepository.findByCode(code)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    TenantBrandingDto dto = toBrandingDto(tenant);
                    log.debug("Serving public branding for tenant code='{}'", code);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                            .body((Object) dto);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Maps a Tenant entity to TenantBrandingDto.
     *
     * @param tenant the tenant entity
     * @return branding DTO with non-sensitive fields only
     */
    private TenantBrandingDto toBrandingDto(Tenant tenant) {
        TenantBrandingDto dto = new TenantBrandingDto();
        dto.setLogoUrl(tenant.getLogoUrl());
        dto.setFaviconUrl(tenant.getFaviconUrl());
        dto.setPrimaryColor(tenant.getPrimaryColor());
        dto.setSecondaryColor(tenant.getSecondaryColor());
        dto.setLoginPageTitle(tenant.getLoginPageTitle());
        dto.setLoginPageSubtitle(tenant.getLoginPageSubtitle());
        dto.setLoginPageBgUrl(tenant.getLoginPageBgUrl());
        return dto;
    }
}
