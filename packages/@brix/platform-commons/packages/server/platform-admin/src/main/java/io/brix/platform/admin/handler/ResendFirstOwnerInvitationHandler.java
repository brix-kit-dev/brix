/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
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
        return FirstOwnerInvitationDto.from(tenantAdministration.resendFirstOwnerInvitation(
            new ResendFirstOwnerInvitationCommand(
                tenantId,
                actorId,
                request.inviteBaseUrl(),
                request.locale())));
    }

    private static ResendFirstOwnerInvitationRequest request(Object body) {
        if (body instanceof ResendFirstOwnerInvitationRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            Object inviteBaseUrl = map.get("inviteBaseUrl");
            if (inviteBaseUrl == null || String.valueOf(inviteBaseUrl).isBlank()) {
                throw new IllegalArgumentException("inviteBaseUrl is required");
            }
            return new ResendFirstOwnerInvitationRequest(
                String.valueOf(inviteBaseUrl),
                PlatformOperationalInvocationSupport.optionalString(body, "locale"));
        }
        throw new IllegalArgumentException("resend first owner invitation request body is required");
    }
}
