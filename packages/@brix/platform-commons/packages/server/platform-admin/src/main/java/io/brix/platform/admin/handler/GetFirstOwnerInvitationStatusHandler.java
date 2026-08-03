/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler that reads the current FIRST_OWNER invitation status.
 *
 * <p>The handler only calls the tenant Data Owner internal contract through the
 * restricted operational context. It never reads tenant repositories or token
 * material.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class GetFirstOwnerInvitationStatusHandler
        implements EndpointHandler<EndpointInvocation<Void>, FirstOwnerInvitationDto> {

    private final TenantAdministration tenantAdministration;

    public GetFirstOwnerInvitationStatusHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
    }

    @Override
    public FirstOwnerInvitationDto handle(EndpointInvocation<Void> invocation) {
        Long tenantId = PlatformOperationalInvocationSupport.requirePathLong(invocation, "tenantId");
        try {
            return tenantAdministration.latestFirstOwnerInvitation(tenantId)
                .map(FirstOwnerInvitationDto::from)
                .orElse(null);
        } catch (TenantAdministrationException ex) {
            throw PlatformEndpointErrors.tenantAdministration(ex);
        }
    }
}
