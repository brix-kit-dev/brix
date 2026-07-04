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

import org.springframework.stereotype.Service;

import io.runtime.sdk.capability.TenantQuotaCapability;

/**
 * Layer 2C implementation of {@link TenantQuotaCapability}.
 *
 * <p>Delegates quota and license admission semantics to the platform tenant
 * service so callers depend on the runtime capability contract, not on
 * persistence details.</p>
 *
 * @since 3.2.3
 */
@Service
public class TenantQuotaCapabilityImpl implements TenantQuotaCapability {

    private final TenantQuotaService tenantQuotaService;

    public TenantQuotaCapabilityImpl(TenantQuotaService tenantQuotaService) {
        this.tenantQuotaService = tenantQuotaService;
    }

    @Override
    public InstallationQuotaSnapshot getInstallationQuota() {
        return tenantQuotaService.getInstallationQuota();
    }
}