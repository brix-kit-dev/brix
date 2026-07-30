/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.brix.platform.admin.dto.PlatformSetupCompleteRequest;
import io.brix.platform.tenant.internal.PlatformIdentityAdministration;
import io.brix.platform.tenant.internal.PlatformSetupCompletionView;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.RuntimeOperationalView;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class CompletePlatformSetupHandlerTest {

    private final PlatformIdentityAdministration identityAdministration =
            org.mockito.Mockito.mock(PlatformIdentityAdministration.class);
    private final CompletePlatformSetupHandler handler =
            new CompletePlatformSetupHandler(new TestOperationalContext(identityAdministration));

    @Test
    void completeDelegatesToIdentityOwner() {
        when(identityAdministration.completeSetup(any())).thenReturn(new PlatformSetupCompletionView(true));

        var response = handler.handle(invocation(
                new PlatformSetupCompleteRequest("setup-token", "challenge", "Password!2026", "123456"),
                Optional.empty()));

        assertEquals(true, response.activated());
    }

    @Test
    void missingBodyMapsToStableBadRequest() {
        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> handler.handle(invocation(null, Optional.empty())));

        assertEquals(400, ex.status());
        assertEquals("PLATFORM_SETUP_INVALID_REQUEST", ex.errorCode());
    }

    @Test
    void tenantContextMapsToStableBadRequest() {
        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> handler.handle(invocation(null, Optional.of("tenant-a"))));

        assertEquals(400, ex.status());
        assertEquals("PLATFORM_SETUP_TENANT_CONTEXT_FORBIDDEN", ex.errorCode());
    }

    @Test
    void ownerNotReadyMapsToStableConflict() {
        when(identityAdministration.completeSetup(any())).thenThrow(
                new IllegalStateException("TOTP enrollment has not been initialized"));

        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> handler.handle(invocation(
                        new PlatformSetupCompleteRequest("setup-token", "challenge", "Password!2026", "123456"),
                        Optional.empty())));

        assertEquals(409, ex.status());
        assertEquals("PLATFORM_SETUP_NOT_READY", ex.errorCode());
    }

    private static EndpointInvocation<PlatformSetupCompleteRequest> invocation(
            PlatformSetupCompleteRequest body,
            Optional<String> tenantId) {
        return new EndpointInvocation<>(
                body,
                Map.of(),
                Map.of(),
                Map.of(),
                tenantId,
                Optional.empty(),
                Optional.of("trace-1"),
                Instant.now().plusSeconds(30));
    }

    private record TestOperationalContext(
            PlatformIdentityAdministration identityAdministration) implements OperationalContext {

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
                    return Set.of("platform-admin");
                }
            };
        }

        @Override
        public <C> C requireInternalContract(Class<C> contractType) {
            if (!contractType.equals(PlatformIdentityAdministration.class)) {
                throw new IllegalArgumentException("Unexpected contract type " + contractType.getName());
            }
            return contractType.cast(identityAdministration);
        }
    }
}
