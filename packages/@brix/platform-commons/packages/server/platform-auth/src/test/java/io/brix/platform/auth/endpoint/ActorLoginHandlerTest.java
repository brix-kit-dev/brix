/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class ActorLoginHandlerTest {

    @Test
    void delegatesToActorLoginAndMapsSelectionResult() {
        AuthFlowCapability authFlow = org.mockito.Mockito.mock(AuthFlowCapability.class);
        when(authFlow.loginActor(org.mockito.ArgumentMatchers.any())).thenReturn(new LoginResult(
            LoginStatus.SELECT_TENANT,
            null,
            null,
            0L,
            "identity-token",
            List.of(),
            42L,
            "First Owner",
            "owner@example.invalid",
            null,
            List.of(),
            List.of(),
            false,
            false));

        ActorLoginHandler handler = new ActorLoginHandler(() -> authFlow);
        LoginResponseDto response = handler.handle(invocation(
            Map.of("loginId", "owner@example.invalid", "password", "Password!2026"),
            Optional.empty()));

        assertEquals(true, response.success());
        assertEquals("SELECT_TENANT", response.status());
        assertEquals("identity-token", response.identityToken());
        assertEquals(42L, response.identityId());
        verify(authFlow).loginActor(argThat(command ->
            "owner@example.invalid".equals(command.loginId())
                && "Password!2026".equals(command.password())
                && "203.0.113.10".equals(command.clientIp())));
    }

    @Test
    void mapsInvalidCredentialsToStableUnauthorizedError() {
        AuthFlowCapability authFlow = org.mockito.Mockito.mock(AuthFlowCapability.class);
        when(authFlow.loginActor(org.mockito.ArgumentMatchers.any())).thenThrow(
            new AuthFlowException(AuthFlowException.CODE_INVALID_CREDENTIALS, "Invalid credentials"));

        ActorLoginHandler handler = new ActorLoginHandler(() -> authFlow);
        EndpointHandlingException error = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                Map.of("loginId", "owner@example.invalid", "password", "wrong"),
                Optional.empty())));

        assertEquals(401, error.status());
        assertEquals(AuthFlowException.CODE_INVALID_CREDENTIALS, error.errorCode());
    }

    @Test
    void rejectsAuthenticatedTenantContext() {
        ActorLoginHandler handler = new ActorLoginHandler(() -> org.mockito.Mockito.mock(AuthFlowCapability.class));

        EndpointHandlingException error = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                Map.of("loginId", "owner@example.invalid", "password", "Password!2026"),
                Optional.of("42"))));

        assertEquals(400, error.status());
        assertEquals("AUTH_TENANT_CONTEXT_FORBIDDEN", error.errorCode());
    }

    private static EndpointInvocation<LoginRequestDto> invocation(
            Object body,
            Optional<String> tenantId) {
        return new EndpointInvocation(
            body,
            Map.of(),
            Map.of(),
            Map.of("x-forwarded-for", List.of("203.0.113.10, 198.51.100.1")),
            tenantId,
            Optional.empty(),
            Optional.empty(),
            Instant.now().plusSeconds(30));
    }
}
