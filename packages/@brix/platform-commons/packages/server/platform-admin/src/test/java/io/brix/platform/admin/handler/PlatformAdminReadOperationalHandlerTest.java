/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.identity.internal.PlatformAdminView;
import io.brix.platform.identity.internal.PlatformIdentityAdministration;
import io.brix.platform.identity.internal.PlatformPageRequest;
import io.brix.platform.identity.internal.PlatformPageView;
import io.brix.platform.tenant.internal.InstallationQuotaView;
import io.brix.platform.tenant.internal.PlatformTenantView;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.RuntimeOperationalView;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class PlatformAdminReadOperationalHandlerTest {

    private final TenantAdministration tenantAdministration = org.mockito.Mockito.mock(TenantAdministration.class);
    private final PlatformIdentityAdministration identityAdministration =
            org.mockito.Mockito.mock(PlatformIdentityAdministration.class);
    private final OperationalContext context = new TestOperationalContext(
            tenantAdministration,
            identityAdministration);

    @Test
    void listAdminsDelegatesToIdentityOwnerReadContract() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(identityAdministration.listPlatformAdmins(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PlatformPageView<>(
                List.of(new PlatformAdminView(
                    10L,
                    100L,
                    "admin",
                    "admin@example.invalid",
                    "PLATFORM_SUPER_ADMIN",
                    "ACTIVE",
                    true,
                    "ops",
                    createdAt)),
                0,
                20,
                1,
                1,
                true,
                true));
        ListPlatformAdminsHandler handler = new ListPlatformAdminsHandler(context);

        var response = handler.handle(invocation(Map.of(
            "page", List.of("0"),
            "size", List.of("20"),
            "sort", List.of("createdAt,desc"))));

        assertEquals(1, response.totalElements());
        assertEquals(10L, response.content().get(0).adminId());
        assertEquals("admin@example.invalid", response.content().get(0).email());
        ArgumentCaptor<PlatformPageRequest> request = ArgumentCaptor.forClass(PlatformPageRequest.class);
        verify(identityAdministration).listPlatformAdmins(request.capture());
        assertEquals(0, request.getValue().page());
        assertEquals(20, request.getValue().size());
        assertEquals("createdAt", request.getValue().sortBy());
        assertEquals(true, request.getValue().descending());
    }

    @Test
    void listTenantsDelegatesToTenantOwnerReadContract() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(tenantAdministration.listTenants(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new io.brix.platform.tenant.internal.PlatformPageView<>(
                List.of(new PlatformTenantView(
                    42L,
                    "acme",
                    "Acme",
                    "PENDING_ACTIVATION",
                    createdAt,
                    createdAt,
                    1,
                    3,
                    "OPEN_CORE_ACTIVE",
                    "zh-CN",
                    "UTC",
                    "LIGHT")),
                0,
                20,
                1,
                1,
                true,
                true));
        ListPlatformTenantsHandler handler = new ListPlatformTenantsHandler(context);

        var response = handler.handle(invocation(Map.of("q", List.of("acme"))));

        assertEquals(42L, response.content().get(0).tenantId());
        assertEquals(3, response.content().get(0).quotaLimit());
        ArgumentCaptor<io.brix.platform.tenant.internal.PlatformPageRequest> request =
            ArgumentCaptor.forClass(io.brix.platform.tenant.internal.PlatformPageRequest.class);
        verify(tenantAdministration).listTenants(request.capture());
        assertEquals("acme", request.getValue().query());
    }

    @Test
    void quotaReadDelegatesToTenantOwner() {
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(tenantAdministration.installationQuota()).thenReturn(new InstallationQuotaView(
            "default",
            3,
            1,
            "OPEN_CORE_ACTIVE",
            null,
            true,
            null,
            updatedAt));
        GetInstallationQuotaHandler handler = new GetInstallationQuotaHandler(context);

        var response = handler.handle(invocation(Map.of()));

        assertEquals("default", response.installationId());
        assertEquals(3, response.quota());
        assertEquals(1, response.used());
        verify(tenantAdministration).installationQuota();
    }

    @Test
    void listTenantsRejectsTenantContext() {
        ListPlatformTenantsHandler handler = new ListPlatformTenantsHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(new EndpointInvocation<>(
            null,
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.of("tenant-a"),
            Optional.of("1001"),
            Optional.of("trace-1"),
            Instant.now().plusSeconds(30))));
        assertEquals(403, failure.status());
        assertEquals("PLATFORM_TENANT_CONTEXT_FORBIDDEN", failure.errorCode());
    }

    @Test
    void listAdminsRejectsMissingPlatformActorWithStableUnauthorizedResponse() {
        ListPlatformAdminsHandler handler = new ListPlatformAdminsHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(new EndpointInvocation<>(
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("trace-1"),
                Instant.now().plusSeconds(30))));

        assertEquals(401, failure.status());
        assertEquals("PLATFORM_AUTH_REQUIRED", failure.errorCode());
    }

    private static EndpointInvocation<Void> invocation(Map<String, List<String>> queryParameters) {
        return new EndpointInvocation<>(
            null,
            Map.of(),
            queryParameters,
            Map.of(),
            Optional.empty(),
            Optional.of("1001"),
            Optional.of("trace-1"),
            Instant.now().plusSeconds(30));
    }

    private record TestOperationalContext(
            TenantAdministration tenantAdministration,
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
            if (contractType.equals(TenantAdministration.class)) {
                return contractType.cast(tenantAdministration);
            }
            if (contractType.equals(PlatformIdentityAdministration.class)) {
                return contractType.cast(identityAdministration);
            }
            throw new IllegalArgumentException("Unexpected contract type " + contractType.getName());
        }
    }
}
