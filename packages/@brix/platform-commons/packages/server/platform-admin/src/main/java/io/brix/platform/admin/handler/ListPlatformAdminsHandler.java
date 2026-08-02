/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.admin.dto.PlatformAdminDto;
import io.brix.platform.admin.dto.PlatformPageDto;
import io.brix.platform.tenant.internal.PlatformAdminView;
import io.brix.platform.tenant.internal.PlatformIdentityAdministration;
import io.brix.platform.tenant.internal.PlatformPageView;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler for the platform administrator list endpoint.
 */
public final class ListPlatformAdminsHandler
        implements EndpointHandler<EndpointInvocation<Void>, PlatformPageDto<PlatformAdminDto>> {

    private final PlatformIdentityAdministration identityAdministration;

    public ListPlatformAdminsHandler(OperationalContext context) {
        this.identityAdministration = context.requireInternalContract(PlatformIdentityAdministration.class);
    }

    @Override
    public PlatformPageDto<PlatformAdminDto> handle(EndpointInvocation<Void> invocation) {
        PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        PlatformPageView<PlatformAdminView> page =
            identityAdministration.listPlatformAdmins(PlatformReadQuerySupport.pageRequest(invocation));
        return new PlatformPageDto<>(
            page.content().stream().map(ListPlatformAdminsHandler::toDto).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages(),
            page.first(),
            page.last());
    }

    private static PlatformAdminDto toDto(PlatformAdminView admin) {
        return new PlatformAdminDto(
            admin.adminId(),
            admin.identityId(),
            admin.username(),
            admin.email(),
            admin.role(),
            admin.status(),
            admin.mfaEnabled(),
            admin.notes(),
            admin.createdAt());
    }
}
