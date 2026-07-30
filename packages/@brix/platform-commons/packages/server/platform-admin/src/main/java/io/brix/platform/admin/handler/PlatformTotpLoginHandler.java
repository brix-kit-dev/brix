/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.admin.dto.PlatformLoginResponse;
import io.brix.platform.admin.dto.PlatformTotpLoginRequest;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/** Handler for platform administrator TOTP login completion. */
public final class PlatformTotpLoginHandler
        implements EndpointHandler<EndpointInvocation<PlatformTotpLoginRequest>, PlatformLoginResponse> {

    private final AuthFlowCapability authFlow;

    public PlatformTotpLoginHandler(OperationalContext context) {
        this.authFlow = context.requireInternalContract(AuthFlowCapability.class);
    }

    @Override
    public PlatformLoginResponse handle(EndpointInvocation<PlatformTotpLoginRequest> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformEndpointErrors.badRequest(
                    "PLATFORM_AUTH_TENANT_CONTEXT_FORBIDDEN",
                    new IllegalArgumentException("platform auth endpoints forbid tenant context"));
        });
        try {
            PlatformTotpLoginRequest request = request(invocation.body());
            var result = authFlow.mfaVerify(new MfaVerifyCommand(
                    request.mfaChallengeToken(),
                    request.totpCode()));
            return PlatformAuthLoginResponseMapper.toResponse(result);
        } catch (AuthFlowException e) {
            throw PlatformEndpointErrors.authFlow(e);
        } catch (IllegalArgumentException e) {
            throw PlatformEndpointErrors.badRequest("PLATFORM_AUTH_INVALID_REQUEST", e);
        }
    }

    private static PlatformTotpLoginRequest request(Object body) {
        if (body instanceof PlatformTotpLoginRequest typed) {
            validate(typed.mfaChallengeToken(), typed.totpCode());
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            PlatformTotpLoginRequest request = new PlatformTotpLoginRequest(
                    string(map, "mfaChallengeToken"),
                    string(map, "totpCode"));
            validate(request.mfaChallengeToken(), request.totpCode());
            return request;
        }
        throw new IllegalArgumentException("platform TOTP login request body is required");
    }

    private static void validate(String mfaChallengeToken, String totpCode) {
        if (mfaChallengeToken == null || mfaChallengeToken.isBlank()) {
            throw new IllegalArgumentException("mfaChallengeToken must not be blank");
        }
        if (totpCode == null || !totpCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("totpCode must be 6 digits");
        }
    }

    private static String string(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value == null ? null : String.valueOf(value);
    }
}
