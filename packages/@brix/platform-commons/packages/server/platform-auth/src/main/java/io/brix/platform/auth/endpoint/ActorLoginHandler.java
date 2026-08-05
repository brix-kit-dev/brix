/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth.endpoint;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginCommand;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Runtime Entry for B-side Actor login.
 */
public final class ActorLoginHandler
        implements EndpointHandler<EndpointInvocation<LoginRequestDto>, LoginResponseDto> {

    private final Supplier<AuthFlowCapability> authFlowSupplier;

    public ActorLoginHandler(Supplier<AuthFlowCapability> authFlowSupplier) {
        this.authFlowSupplier = Objects.requireNonNull(authFlowSupplier, "authFlowSupplier must not be null");
    }

    @Override
    public LoginResponseDto handle(EndpointInvocation<LoginRequestDto> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformAuthEndpointErrors.badRequest(
                "AUTH_TENANT_CONTEXT_FORBIDDEN",
                "Actor login forbids authenticated tenant context");
        });
        try {
            LoginRequestDto request = request(invocation.body());
            return LoginResponseDto.from(authFlow().loginActor(new LoginCommand(
                request.loginId(),
                request.password(),
                clientIp(invocation))));
        } catch (AuthFlowException e) {
            throw PlatformAuthEndpointErrors.authFlow(e);
        } catch (IllegalArgumentException e) {
            throw PlatformAuthEndpointErrors.badRequest("AUTH_INVALID_REQUEST", e.getMessage());
        }
    }

    private AuthFlowCapability authFlow() {
        AuthFlowCapability authFlow = authFlowSupplier.get();
        if (authFlow == null) {
            throw PlatformAuthEndpointErrors.serviceUnavailable(
                "AUTH_FLOW_UNAVAILABLE",
                "Auth flow capability is unavailable");
        }
        return authFlow;
    }

    private static LoginRequestDto request(Object body) {
        if (body instanceof LoginRequestDto typed) {
            validate(typed.loginId(), typed.password());
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            LoginRequestDto request = new LoginRequestDto(
                string(map, "loginId"),
                string(map, "password"));
            validate(request.loginId(), request.password());
            return request;
        }
        throw new IllegalArgumentException("login request body is required");
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
