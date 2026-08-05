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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.brix.platform.admin.dto.BootstrapSessionRequest;
import io.brix.platform.admin.dto.CreateFirstAdminRequest;
import io.brix.platform.identity.internal.BootstrapSessionView;
import io.brix.platform.identity.internal.PlatformAdminCreationView;
import io.brix.platform.identity.internal.PlatformBootstrapAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.RuntimeOperationalView;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class BootstrapHandlerErrorMappingTest {

    private final PlatformBootstrapAdministration bootstrapAdministration =
            org.mockito.Mockito.mock(PlatformBootstrapAdministration.class);
    private final TestOperationalContext context = new TestOperationalContext(bootstrapAdministration);

    @Test
    void openSessionDelegatesToBootstrapOwner() {
        when(bootstrapAdministration.openSession(any()))
                .thenReturn(new BootstrapSessionView("BOOTSTRAP_SETUP", "bootstrap-token", 300));

        var response = new OpenBootstrapSessionHandler(context)
                .handle(invocation(new BootstrapSessionRequest("setup-code"), Map.of(), Optional.empty()));

        assertEquals("BOOTSTRAP_SETUP", response.tokenType());
        assertEquals("bootstrap-token", response.accessToken());
        assertEquals(300, response.expiresIn());
    }

    @Test
    void openSessionInvalidRequestMapsToStableBadRequest() {
        when(bootstrapAdministration.openSession(any()))
                .thenThrow(new IllegalArgumentException("bootstrap setup code is invalid"));

        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> new OpenBootstrapSessionHandler(context)
                        .handle(invocation(new BootstrapSessionRequest("bad-code"), Map.of(), Optional.empty())));

        assertEquals(400, ex.status());
        assertEquals("PLATFORM_BOOTSTRAP_INVALID_REQUEST", ex.errorCode());
    }

    @Test
    void openSessionTenantContextMapsToStableBadRequest() {
        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> new OpenBootstrapSessionHandler(context)
                        .handle(invocation(null, Map.of(), Optional.of("tenant-a"))));

        assertEquals(400, ex.status());
        assertEquals("PLATFORM_BOOTSTRAP_TENANT_CONTEXT_FORBIDDEN", ex.errorCode());
    }

    @Test
    void openSessionOwnerNotReadyMapsToStableConflict() {
        when(bootstrapAdministration.openSession(any()))
                .thenThrow(new IllegalStateException("bootstrap is closed"));

        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> new OpenBootstrapSessionHandler(context)
                        .handle(invocation(new BootstrapSessionRequest("setup-code"), Map.of(), Optional.empty())));

        assertEquals(409, ex.status());
        assertEquals("PLATFORM_BOOTSTRAP_NOT_READY", ex.errorCode());
    }

    @Test
    void createFirstAdminDelegatesToBootstrapOwner() {
        when(bootstrapAdministration.createFirstAdmin(any()))
                .thenReturn(new PlatformAdminCreationView(100L, 200L, true));

        var response = new CreateFirstAdminHandler(context).handle(invocation(
                new CreateFirstAdminRequest("Owner", "owner@example.invalid", null),
                Map.of("authorization", List.of("Bearer bootstrap-token")),
                Optional.empty()));

        assertEquals(100L, response.id());
        assertEquals(200L, response.identityId());
        assertEquals(true, response.setupLinkSent());
    }

    @Test
    void createFirstAdminMissingBearerMapsToStableBadRequest() {
        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> new CreateFirstAdminHandler(context).handle(invocation(
                        new CreateFirstAdminRequest("Owner", "owner@example.invalid", null),
                        Map.of(),
                        Optional.empty())));

        assertEquals(400, ex.status());
        assertEquals("PLATFORM_BOOTSTRAP_INVALID_REQUEST", ex.errorCode());
    }

    @Test
    void createFirstAdminTenantContextMapsToStableBadRequest() {
        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> new CreateFirstAdminHandler(context).handle(invocation(null, Map.of(), Optional.of("tenant-a"))));

        assertEquals(400, ex.status());
        assertEquals("PLATFORM_BOOTSTRAP_TENANT_CONTEXT_FORBIDDEN", ex.errorCode());
    }

    @Test
    void createFirstAdminOwnerNotReadyMapsToStableConflict() {
        when(bootstrapAdministration.createFirstAdmin(any()))
                .thenThrow(new IllegalStateException("bootstrap is closed"));

        EndpointHandlingException ex = assertThrows(
                EndpointHandlingException.class,
                () -> new CreateFirstAdminHandler(context).handle(invocation(
                        new CreateFirstAdminRequest("Owner", "owner@example.invalid", null),
                        Map.of("authorization", List.of("Bearer bootstrap-token")),
                        Optional.empty())));

        assertEquals(409, ex.status());
        assertEquals("PLATFORM_BOOTSTRAP_NOT_READY", ex.errorCode());
    }

    private static <T> EndpointInvocation<T> invocation(
            T body,
            Map<String, List<String>> headers,
            Optional<String> tenantId) {
        return new EndpointInvocation<>(
                body,
                Map.of(),
                Map.of(),
                headers,
                tenantId,
                Optional.empty(),
                Optional.of("trace-1"),
                Instant.now().plusSeconds(30));
    }

    private record TestOperationalContext(
            PlatformBootstrapAdministration bootstrapAdministration) implements OperationalContext {

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
            if (!contractType.equals(PlatformBootstrapAdministration.class)) {
                throw new IllegalArgumentException("Unexpected contract type " + contractType.getName());
            }
            return contractType.cast(bootstrapAdministration);
        }
    }
}
