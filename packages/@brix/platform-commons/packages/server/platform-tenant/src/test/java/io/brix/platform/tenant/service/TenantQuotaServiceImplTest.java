/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.tenant.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brix.platform.tenant.entity.InstallationQuota;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.runtime.sdk.capability.TenantQuotaCapability.InstallationQuotaSnapshot;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantQuotaServiceImpl Tests")
class TenantQuotaServiceImplTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository memberRepository;

    @Mock
    private TenantPrincipalRepository principalRepository;

    @Mock
    private InstallationQuotaRepository installationQuotaRepository;

    @Test
    @DisplayName("should expose open-core installation quota when capacity remains")
    void shouldExposeOpenCoreInstallationQuotaWhenCapacityRemains() {
        InstallationQuota quota = new InstallationQuota("default", 3, 2);
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        quota.setUpdatedAt(updatedAt);
        when(installationQuotaRepository.findById("default")).thenReturn(Optional.of(quota));

        InstallationQuotaSnapshot snapshot = service().getInstallationQuota();

        assertEquals("default", snapshot.installationId());
        assertEquals(3, snapshot.quota());
        assertEquals(2, snapshot.used());
        assertEquals("OPEN_CORE_ACTIVE", snapshot.licenseStatus());
        assertTrue(snapshot.canCreateTenant());
        assertNull(snapshot.refusalReason());
        assertEquals(updatedAt, snapshot.updatedAt());
    }

    @Test
    @DisplayName("should expose quota refusal reason when installation quota is full")
    void shouldExposeQuotaRefusalReasonWhenInstallationQuotaIsFull() {
        when(installationQuotaRepository.findById("default"))
                .thenReturn(Optional.of(new InstallationQuota("default", 3, 3)));

        InstallationQuotaSnapshot snapshot = service().getInstallationQuota();

        assertFalse(snapshot.canCreateTenant());
        assertEquals("TENANT_QUOTA_EXCEEDED", snapshot.refusalReason());
    }

    private TenantQuotaServiceImpl service() {
        return new TenantQuotaServiceImpl(
                tenantRepository,
                memberRepository,
                principalRepository,
                installationQuotaRepository);
    }
}