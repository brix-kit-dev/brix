/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.admin.dto.InstallationQuotaDto;
import io.brix.platform.tenant.internal.InstallationQuotaView;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler for the installation quota read endpoint.
 */
public final class GetInstallationQuotaHandler
        implements EndpointHandler<EndpointInvocation<Void>, InstallationQuotaDto> {

    private final TenantAdministration tenantAdministration;

    public GetInstallationQuotaHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
    }

    @Override
    public InstallationQuotaDto handle(EndpointInvocation<Void> invocation) {
        PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        InstallationQuotaView quota = tenantAdministration.installationQuota();
        return new InstallationQuotaDto(
            quota.installationId(),
            quota.quota(),
            quota.used(),
            quota.licenseStatus(),
            quota.expiresAt(),
            quota.canCreateTenant(),
            quota.refusalReason(),
            quota.updatedAt());
    }
}
