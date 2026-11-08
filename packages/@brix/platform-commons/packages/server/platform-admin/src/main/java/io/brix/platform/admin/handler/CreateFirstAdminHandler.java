/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.admin.dto.CreateFirstAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.identity.internal.CreateFirstPlatformAdminCommand;
import io.brix.platform.identity.internal.PlatformBootstrapAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Handler for creating the first formal platform super administrator.
 *
 * <p>Its caller is the BOOTSTRAP_SETUP session the runtime verified before the handler (standard-link plan point
 * 10b-7a): the handler names that session to the identity owner, which spends it, and never sees its token.</p>
 */
public final class CreateFirstAdminHandler
        implements EndpointHandler<EndpointInvocation<CreateFirstAdminRequest>, CreatePlatformAdminResponse> {

    private final PlatformBootstrapAdministration bootstrapAdministration;

    public CreateFirstAdminHandler(OperationalContext context) {
        this.bootstrapAdministration = context.requireInternalContract(PlatformBootstrapAdministration.class);
    }

    @Override
    public CreatePlatformAdminResponse handle(EndpointInvocation<CreateFirstAdminRequest> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformEndpointErrors.badRequest(
                    "PLATFORM_BOOTSTRAP_TENANT_CONTEXT_FORBIDDEN",
                    new IllegalArgumentException("bootstrap endpoints forbid tenant context"));
        });
        String session = invocation.actorId()
                .orElseThrow(() -> PlatformEndpointErrors.unauthorized(
                        "PLATFORM_BOOTSTRAP_SESSION_REQUIRED",
                        "a verified bootstrap session is required"));
        try {
            CreateFirstAdminRequest request = request(invocation.body());
            var created = bootstrapAdministration.createFirstAdmin(new CreateFirstPlatformAdminCommand(
                    session,
                    request.username(),
                    request.email(),
                    request.notes()));
            return new CreatePlatformAdminResponse(created.id(), created.identityId(), created.setupLinkSent());
        } catch (IllegalArgumentException e) {
            throw PlatformEndpointErrors.badRequest("PLATFORM_BOOTSTRAP_INVALID_REQUEST", e);
        } catch (IllegalStateException e) {
            throw PlatformEndpointErrors.conflict("PLATFORM_BOOTSTRAP_NOT_READY", e);
        }
    }

    private static CreateFirstAdminRequest request(Object body) {
        if (body instanceof CreateFirstAdminRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            return new CreateFirstAdminRequest(
                    string(map, "username"),
                    string(map, "email"),
                    string(map, "notes"));
        }
        throw new IllegalArgumentException("create first admin request body is required");
    }

    private static String string(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value == null ? null : String.valueOf(value);
    }
}
