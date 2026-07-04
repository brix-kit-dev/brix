/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.admin.dto.InstallationQuotaDto;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.annotation.RequirePermission;
import io.runtime.sdk.capability.TenantQuotaCapability;
import io.runtime.sdk.capability.TenantQuotaCapability.InstallationQuotaSnapshot;

/**
 * Platform license and tenant quota endpoint.
 *
 * <p>This controller is read-only and exposes the installation-level quota
 * snapshot through the runtime capability contract.</p>
 */
@RestController
@RequestMapping("/api/platform/license")
public class PlatformLicenseController {

    private final TenantQuotaCapability tenantQuotaCapability;

    public PlatformLicenseController(TenantQuotaCapability tenantQuotaCapability) {
        this.tenantQuotaCapability = tenantQuotaCapability;
    }

    /**
     * Returns current installation quota and license admission state.
     *
     * @return installation quota response
     */
    @GetMapping("/quota")
    @RequirePermission(PlatformPermissions.LICENSE_READ)
    public ResponseEntity<InstallationQuotaDto> getInstallationQuota() {
        InstallationQuotaSnapshot snapshot = tenantQuotaCapability.getInstallationQuota();
        return ResponseEntity.ok(new InstallationQuotaDto(
                snapshot.installationId(),
                snapshot.quota(),
                snapshot.used(),
                snapshot.licenseStatus(),
                snapshot.expiresAt(),
                snapshot.canCreateTenant(),
                snapshot.refusalReason(),
                snapshot.updatedAt()));
    }
}