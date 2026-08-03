/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.tenant.internal.CreateFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler that requests a FIRST_OWNER invitation from tenant Owner.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class CreateFirstOwnerInvitationHandler
        implements EndpointHandler<EndpointInvocation<CreateFirstOwnerInvitationRequest>, FirstOwnerInvitationDto> {

    private final TenantAdministration tenantAdministration;

    public CreateFirstOwnerInvitationHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
    }

    @Override
    public FirstOwnerInvitationDto handle(EndpointInvocation<CreateFirstOwnerInvitationRequest> invocation) {
        Long actorId = PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        Long tenantId = PlatformOperationalInvocationSupport.requirePathLong(invocation, "tenantId");
        CreateFirstOwnerInvitationRequest request = request(invocation.body());
        try {
            return FirstOwnerInvitationDto.from(tenantAdministration.createFirstOwnerInvitation(
                new CreateFirstOwnerInvitationCommand(
                    tenantId,
                    request.inviteeEmail(),
                    PlatformOperationalInvocationSupport.platformOperatorRef(actorId),
                    request.locale())));
        } catch (TenantAdministrationException ex) {
            throw PlatformEndpointErrors.tenantAdministration(ex);
        }
    }

    private static CreateFirstOwnerInvitationRequest request(Object body) {
        if (body instanceof CreateFirstOwnerInvitationRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            return new CreateFirstOwnerInvitationRequest(
                requiredString(map, "inviteeEmail"),
                PlatformOperationalInvocationSupport.optionalString(body, "locale"));
        }
        throw new IllegalArgumentException("first owner invitation request body is required");
    }

    private static String requiredString(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value);
    }
}
