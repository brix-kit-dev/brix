/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.admin.dto.PlatformLoginRequest;
import io.brix.platform.admin.dto.PlatformLoginResponse;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginCommand;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/** Handler for platform administrator login. */
public final class PlatformLoginHandler
        implements EndpointHandler<EndpointInvocation<PlatformLoginRequest>, PlatformLoginResponse> {

    private final AuthFlowCapability authFlow;

    public PlatformLoginHandler(OperationalContext context) {
        this.authFlow = context.requireInternalContract(AuthFlowCapability.class);
    }

    @Override
    public PlatformLoginResponse handle(EndpointInvocation<PlatformLoginRequest> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformEndpointErrors.badRequest(
                    "PLATFORM_AUTH_TENANT_CONTEXT_FORBIDDEN",
                    new IllegalArgumentException("platform auth endpoints forbid tenant context"));
        });
        try {
            PlatformLoginRequest request = request(invocation.body());
            var result = authFlow.loginPlatformAdmin(new LoginCommand(
                    request.loginId(),
                    request.password(),
                    clientIp(invocation)));
            return PlatformAuthLoginResponseMapper.toResponse(result);
        } catch (AuthFlowException e) {
            throw PlatformEndpointErrors.authFlow(e);
        } catch (IllegalArgumentException e) {
            throw PlatformEndpointErrors.badRequest("PLATFORM_AUTH_INVALID_REQUEST", e);
        }
    }

    private static PlatformLoginRequest request(Object body) {
        if (body instanceof PlatformLoginRequest typed) {
            validate(typed.loginId(), typed.password());
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            PlatformLoginRequest request = new PlatformLoginRequest(
                    string(map, "loginId"),
                    string(map, "password"));
            validate(request.loginId(), request.password());
            return request;
        }
        throw new IllegalArgumentException("platform login request body is required");
    }

    private static void validate(String loginId, String password) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("loginId must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }

    private static String string(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private static String clientIp(EndpointInvocation<?> invocation) {
        return invocation.headers().getOrDefault("x-forwarded-for", java.util.List.of()).stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.split(",", 2)[0].trim())
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElseGet(() -> invocation.headers().getOrDefault("x-real-ip", java.util.List.of()).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null));
    }
}
