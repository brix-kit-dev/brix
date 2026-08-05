/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.admin.dto.PlatformSetupTotpInitRequest;
import io.brix.platform.admin.dto.PlatformSetupTotpInitResponse;
import io.brix.platform.identity.internal.PlatformIdentityAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/** Handler for initializing setup-time TOTP enrollment. */
public final class InitPlatformSetupTotpHandler
        implements EndpointHandler<EndpointInvocation<PlatformSetupTotpInitRequest>, PlatformSetupTotpInitResponse> {

    private final PlatformIdentityAdministration identityAdministration;

    public InitPlatformSetupTotpHandler(OperationalContext context) {
        this.identityAdministration = context.requireInternalContract(PlatformIdentityAdministration.class);
    }

    @Override
    public PlatformSetupTotpInitResponse handle(EndpointInvocation<PlatformSetupTotpInitRequest> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw new IllegalArgumentException("setup endpoints forbid tenant context");
        });
        var challenge = identityAdministration.initTotp(request(invocation.body()).setupToken());
        return new PlatformSetupTotpInitResponse(challenge.challengeId(), challenge.otpauthUri());
    }

    private static PlatformSetupTotpInitRequest request(Object body) {
        if (body instanceof PlatformSetupTotpInitRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            Object value = map.get("token");
            return new PlatformSetupTotpInitRequest(value == null ? null : String.valueOf(value));
        }
        throw new IllegalArgumentException("setup TOTP init request body is required");
    }
}
