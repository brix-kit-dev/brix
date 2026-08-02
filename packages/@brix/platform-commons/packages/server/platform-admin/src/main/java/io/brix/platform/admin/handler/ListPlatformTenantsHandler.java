/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.admin.dto.PlatformPageDto;
import io.brix.platform.admin.dto.PlatformTenantDto;
import io.brix.platform.tenant.internal.PlatformPageView;
import io.brix.platform.tenant.internal.PlatformTenantView;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler for the tenant list endpoint.
 */
public final class ListPlatformTenantsHandler
        implements EndpointHandler<EndpointInvocation<Void>, PlatformPageDto<PlatformTenantDto>> {

    private final TenantAdministration tenantAdministration;

    public ListPlatformTenantsHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
    }

    @Override
    public PlatformPageDto<PlatformTenantDto> handle(EndpointInvocation<Void> invocation) {
        PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        PlatformPageView<PlatformTenantView> page =
            tenantAdministration.listTenants(PlatformReadQuerySupport.pageRequest(invocation));
        return new PlatformPageDto<>(
            page.content().stream().map(ListPlatformTenantsHandler::toDto).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages(),
            page.first(),
            page.last());
    }

    private static PlatformTenantDto toDto(PlatformTenantView tenant) {
        return new PlatformTenantDto(
            tenant.tenantId(),
            tenant.code(),
            tenant.name(),
            tenant.status(),
            tenant.createdAt(),
            tenant.updatedAt(),
            tenant.quotaUsed(),
            tenant.quotaLimit(),
            tenant.licenseStatus(),
            tenant.defaultLocale(),
            tenant.defaultTimezone(),
            tenant.defaultTheme());
    }
}
