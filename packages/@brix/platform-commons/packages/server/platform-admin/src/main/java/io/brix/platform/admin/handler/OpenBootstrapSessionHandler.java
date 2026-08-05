/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.admin.dto.BootstrapSessionRequest;
import io.brix.platform.admin.dto.BootstrapSessionResponse;
import io.brix.platform.identity.internal.BootstrapSessionCommand;
import io.brix.platform.identity.internal.PlatformBootstrapAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/** Handler for exchanging a setup code for a BOOTSTRAP_SETUP session. */
public final class OpenBootstrapSessionHandler
        implements EndpointHandler<EndpointInvocation<BootstrapSessionRequest>, BootstrapSessionResponse> {

    private final PlatformBootstrapAdministration bootstrapAdministration;

    public OpenBootstrapSessionHandler(OperationalContext context) {
        this.bootstrapAdministration = context.requireInternalContract(PlatformBootstrapAdministration.class);
    }

    @Override
    public BootstrapSessionResponse handle(EndpointInvocation<BootstrapSessionRequest> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformEndpointErrors.badRequest(
                    "PLATFORM_BOOTSTRAP_TENANT_CONTEXT_FORBIDDEN",
                    new IllegalArgumentException("bootstrap endpoints forbid tenant context"));
        });
        try {
            var session = bootstrapAdministration.openSession(
                    new BootstrapSessionCommand(request(invocation.body()).setupCode()));
            return new BootstrapSessionResponse(session.tokenType(), session.accessToken(), session.expiresIn());
        } catch (IllegalArgumentException e) {
            throw PlatformEndpointErrors.badRequest("PLATFORM_BOOTSTRAP_INVALID_REQUEST", e);
        } catch (IllegalStateException e) {
            throw PlatformEndpointErrors.conflict("PLATFORM_BOOTSTRAP_NOT_READY", e);
        }
    }

    private static BootstrapSessionRequest request(Object body) {
        if (body instanceof BootstrapSessionRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            Object value = map.get("setupCode");
            return new BootstrapSessionRequest(value == null ? null : String.valueOf(value));
        }
        throw new IllegalArgumentException("bootstrap session request body is required");
    }
}
