/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.admin.dto.PlatformSetupCompleteRequest;
import io.brix.platform.admin.dto.PlatformSetupCompleteResponse;
import io.brix.platform.tenant.internal.CompletePlatformSetupCommand;
import io.brix.platform.tenant.internal.PlatformIdentityAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/** Handler for completing platform password and TOTP setup. */
public final class CompletePlatformSetupHandler
        implements EndpointHandler<EndpointInvocation<PlatformSetupCompleteRequest>, PlatformSetupCompleteResponse> {

    private final PlatformIdentityAdministration identityAdministration;

    public CompletePlatformSetupHandler(OperationalContext context) {
        this.identityAdministration = context.requireInternalContract(PlatformIdentityAdministration.class);
    }

    @Override
    public PlatformSetupCompleteResponse handle(EndpointInvocation<PlatformSetupCompleteRequest> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw new IllegalArgumentException("setup endpoints forbid tenant context");
        });
        PlatformSetupCompleteRequest request = request(invocation.body());
        var completion = identityAdministration.completeSetup(new CompletePlatformSetupCommand(
                request.setupToken(),
                request.challengeId(),
                request.password(),
                request.totpCode()));
        return new PlatformSetupCompleteResponse(completion.activated());
    }

    private static PlatformSetupCompleteRequest request(Object body) {
        if (body instanceof PlatformSetupCompleteRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            return new PlatformSetupCompleteRequest(
                    string(map, "token"),
                    string(map, "challengeId"),
                    string(map, "password"),
                    string(map, "totpCode"));
        }
        throw new IllegalArgumentException("setup complete request body is required");
    }

    private static String string(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value == null ? null : String.valueOf(value);
    }
}
