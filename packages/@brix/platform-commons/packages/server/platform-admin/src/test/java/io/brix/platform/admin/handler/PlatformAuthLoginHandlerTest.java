/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.brix.platform.admin.dto.PlatformLoginRequest;
import io.brix.platform.admin.dto.PlatformTotpLoginRequest;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.RuntimeOperationalView;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class PlatformAuthLoginHandlerTest {

    private final AuthFlowCapability authFlow =
            org.mockito.Mockito.mock(AuthFlowCapability.class);
    private final PlatformLoginHandler loginHandler =
            new PlatformLoginHandler(new TestOperationalContext(authFlow));
    private final PlatformTotpLoginHandler totpHandler =
            new PlatformTotpLoginHandler(new TestOperationalContext(authFlow));

    @Test
    void loginDelegatesToInternalContractAndMapsMfaChallenge() {
        when(authFlow.loginPlatformAdmin(any())).thenReturn(new LoginResult(
                LoginStatus.MFA_REQUIRED,
                null,
                null,
                null,
                "challenge-token",
                null,
                42L,
                "Platform Admin",
                "admin@example.com",
                "PLATFORM_SUPER_ADMIN",
                List.of("PLATFORM_SUPER_ADMIN"),
                List.of("platform:*"),
                false,
                true));

        var response = loginHandler.handle(invocation(
                new PlatformLoginRequest("admin@example.com", "Password!2026"),
                Optional.empty()));

        assertEquals("MFA_REQUIRED", response.status());
        assertEquals("challenge-token", response.mfaChallengeToken());
        assertEquals(0L, response.expiresIn());
        assertEquals("PLATFORM_SUPER_ADMIN", response.platformRole());
    }

    @Test
    void totpDelegatesToInternalContractAndMapsCompleteTokenResponse() {
        when(authFlow.mfaVerify(any())).thenReturn(new LoginResult(
                LoginStatus.COMPLETE,
                "access-token",
                "refresh-token",
                3600L,
                null,
                null,
                42L,
                "Platform Admin",
                "admin@example.com",
                "PLATFORM_SUPER_ADMIN",
                List.of("PLATFORM_SUPER_ADMIN"),
                List.of("platform:*"),
                false,
                false));

        var response = totpHandler.handle(invocation(
                new PlatformTotpLoginRequest("challenge-token", "123456"),
                Optional.empty()));

        assertEquals("COMPLETE", response.status());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(3600L, response.expiresIn());
        assertNull(response.mfaChallengeToken());
    }

    @Test
    void authFlowExceptionMapsToStableEndpointStatus() {
        when(authFlow.loginPlatformAdmin(any())).thenThrow(new AuthFlowException(
                AuthFlowException.CODE_INVALID_CREDENTIALS,
                "Invalid credentials"));

        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> loginHandler.handle(invocation(
                        new PlatformLoginRequest("admin@example.com", "wrong"),
                        Optional.empty())));

        assertEquals(401, ex.status());
        assertEquals(AuthFlowException.CODE_INVALID_CREDENTIALS, ex.errorCode());
    }

    @Test
    void tenantContextIsRejectedBeforeAuthFlow() {
        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> loginHandler.handle(invocation(
                        new PlatformLoginRequest("admin@example.com", "Password!2026"),
                        Optional.of("tenant-a"))));

        assertEquals(400, ex.status());
        assertEquals("PLATFORM_AUTH_TENANT_CONTEXT_FORBIDDEN", ex.errorCode());
    }

    private static <T> EndpointInvocation<T> invocation(T body, Optional<String> tenantId) {
        return new EndpointInvocation<>(
                body,
                Map.of(),
                Map.of(),
                Map.of("x-forwarded-for", List.of("203.0.113.10")),
                tenantId,
                Optional.empty(),
                Optional.of("trace-1"),
                Instant.now().plusSeconds(30));
    }

    private record TestOperationalContext(AuthFlowCapability authFlow) implements OperationalContext {

        @Override
        public OperationalModuleIdentity moduleIdentity() {
            return new OperationalModuleIdentity("platform-admin", "3.2.0", "platform-commons");
        }

        @Override
        public RuntimeOperationalView runtimeView() {
            return new RuntimeOperationalView() {
                @Override
                public long entryGeneration() {
                    return 1L;
                }

                @Override
                public boolean ready() {
                    return true;
                }

                @Override
                public Set<String> requiredModuleIds() {
                    return Set.of("platform-auth-flow", "platform-admin");
                }
            };
        }

        @Override
        public <C> C requireInternalContract(Class<C> contractType) {
            if (!contractType.equals(AuthFlowCapability.class)) {
                throw new IllegalArgumentException("Unexpected contract type " + contractType.getName());
            }
            return contractType.cast(authFlow);
        }
    }
}
