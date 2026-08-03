/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler that asks the tenant Owner to resend FIRST_OWNER invite.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class ResendFirstOwnerInvitationHandler
        implements EndpointHandler<EndpointInvocation<ResendFirstOwnerInvitationRequest>, FirstOwnerInvitationDto> {

    private final TenantAdministration tenantAdministration;

    public ResendFirstOwnerInvitationHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
    }

    @Override
    public FirstOwnerInvitationDto handle(EndpointInvocation<ResendFirstOwnerInvitationRequest> invocation) {
        Long actorId = PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        Long tenantId = PlatformOperationalInvocationSupport.requirePathLong(invocation, "tenantId");
        ResendFirstOwnerInvitationRequest request = request(invocation.body());
        try {
            return FirstOwnerInvitationDto.from(tenantAdministration.resendFirstOwnerInvitation(
                new ResendFirstOwnerInvitationCommand(
                    tenantId,
                    PlatformOperationalInvocationSupport.platformOperatorRef(actorId),
                    request.locale())));
        } catch (TenantAdministrationException ex) {
            throw PlatformEndpointErrors.tenantAdministration(ex);
        }
    }

    private static ResendFirstOwnerInvitationRequest request(Object body) {
        if (body instanceof ResendFirstOwnerInvitationRequest typed) {
            return typed;
        }
        if (body instanceof java.util.Map<?, ?>) {
            return new ResendFirstOwnerInvitationRequest(
                PlatformOperationalInvocationSupport.optionalString(body, "locale"));
        }
        if (body == null) {
            return new ResendFirstOwnerInvitationRequest(null);
        }
        throw new IllegalArgumentException("resend first owner invitation request body must be an object");
    }
}
