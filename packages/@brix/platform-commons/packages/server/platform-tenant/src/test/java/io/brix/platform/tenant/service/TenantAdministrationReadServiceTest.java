/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.enums.ThemeMode;
import io.brix.platform.tenant.internal.PlatformPageRequest;
import io.brix.platform.tenant.repository.TenantRepository;
import io.runtime.sdk.capability.TenantQuotaCapability.InstallationQuotaSnapshot;

class TenantAdministrationReadServiceTest {

    private final TenantProvisioningService tenantProvisioningService = mock(TenantProvisioningService.class);
    private final FirstOwnerInvitationService firstOwnerInvitationService = mock(FirstOwnerInvitationService.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final TenantQuotaService tenantQuotaService = mock(TenantQuotaService.class);

    @Test
    void listTenantsReturnsOwnerWhitelistViewWithInstallationQuota() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        Tenant tenant = new Tenant("acme", "Acme");
        tenant.setId(42L);
        tenant.setStatus(TenantStatus.PENDING_ACTIVATION);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenant.setDefaultLocale("zh-CN");
        tenant.setDefaultTimezone("UTC");
        tenant.setDefaultTheme(ThemeMode.LIGHT);
        when(tenantQuotaService.getInstallationQuota()).thenReturn(new InstallationQuotaSnapshot(
            "default",
            3,
            1,
            "OPEN_CORE_ACTIVE",
            null,
            true,
            null,
            now));
        when(tenantRepository.findPlatformAdminPageByStatusAndTerm(
                eq(TenantStatus.PENDING_ACTIVATION), eq("acme"), any()))
            .thenReturn(new PageImpl<>(List.of(tenant)));

        var page = service().listTenants(new PlatformPageRequest(
            0,
            20,
            "createdAt",
            true,
            "PENDING_ACTIVATION",
            "acme"));

        assertEquals(1, page.totalElements());
        assertEquals(42L, page.content().get(0).tenantId());
        assertEquals("OPEN_CORE_ACTIVE", page.content().get(0).licenseStatus());
        assertEquals(3, page.content().get(0).quotaLimit());
        verify(tenantRepository).findPlatformAdminPageByStatusAndTerm(
                eq(TenantStatus.PENDING_ACTIVATION), eq("acme"), any());
    }

    @Test
    void listTenantsUsesTypeStableQueryWhenSearchTermIsBlank() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(tenantQuotaService.getInstallationQuota()).thenReturn(new InstallationQuotaSnapshot(
            "default",
            3,
            0,
            "OPEN_CORE_ACTIVE",
            null,
            true,
            null,
            now));
        when(tenantRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service().listTenants(new PlatformPageRequest(
            0,
            20,
            "createdAt",
            true,
            null,
            " "));

        verify(tenantRepository).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void installationQuotaReturnsOwnerQuotaSnapshot() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(tenantQuotaService.getInstallationQuota()).thenReturn(new InstallationQuotaSnapshot(
            "default",
            3,
            3,
            "OPEN_CORE_ACTIVE",
            null,
            false,
            "TENANT_QUOTA_EXCEEDED",
            now));

        var quota = service().installationQuota();

        assertEquals("default", quota.installationId());
        assertEquals(3, quota.quota());
        assertEquals(3, quota.used());
        assertEquals(false, quota.canCreateTenant());
        assertEquals("TENANT_QUOTA_EXCEEDED", quota.refusalReason());
    }

    private TenantAdministrationService service() {
        return new TenantAdministrationService(
            tenantProvisioningService,
            firstOwnerInvitationService,
            tenantRepository,
            tenantQuotaService);
    }
}
