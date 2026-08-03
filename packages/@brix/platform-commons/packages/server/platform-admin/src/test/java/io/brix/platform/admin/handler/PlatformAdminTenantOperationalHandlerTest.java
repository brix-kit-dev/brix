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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.admin.dto.CreatePlatformTenantRequest;
import io.brix.platform.tenant.internal.CreateFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.CreatePendingTenantCommand;
import io.brix.platform.tenant.internal.FirstOwnerInvitationView;
import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.RevokeFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.brix.platform.tenant.internal.TenantAdministrationTenant;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.RuntimeOperationalView;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class PlatformAdminTenantOperationalHandlerTest {

    private final TenantAdministration tenantAdministration = org.mockito.Mockito.mock(TenantAdministration.class);
    private final OperationalContext context = new TestOperationalContext(tenantAdministration);

    @Test
    void createTenantDelegatesToTenantAdministrationWithoutOwnerDataAccess() {
        when(tenantAdministration.createPendingTenant(any())).thenReturn(
            new TenantAdministrationTenant(42L, "acme", "Acme", "PENDING_ACTIVATION"));
        CreatePlatformTenantHandler handler = new CreatePlatformTenantHandler(context);

        var response = handler.handle(invocation(
            new CreatePlatformTenantRequest("acme", "Acme"),
            Map.of(),
            Optional.of("1001"),
            Optional.empty()));

        assertEquals(42L, response.tenantId());
        assertEquals("PENDING_ACTIVATION", response.status());
        ArgumentCaptor<CreatePendingTenantCommand> command =
            ArgumentCaptor.forClass(CreatePendingTenantCommand.class);
        verify(tenantAdministration).createPendingTenant(command.capture());
        assertEquals("acme", command.getValue().code());
        assertEquals(1001L, command.getValue().platformAdminIdentityId());
    }

    @Test
    void createTenantRejectsTenantContext() {
        CreatePlatformTenantHandler handler = new CreatePlatformTenantHandler(context);

        EndpointHandlingException failure = assertThrows(EndpointHandlingException.class, () -> handler.handle(invocation(
            new CreatePlatformTenantRequest("acme", "Acme"),
            Map.of(),
            Optional.of("1001"),
            Optional.of("tenant-a"))));
        assertEquals(403, failure.status());
        assertEquals("PLATFORM_TENANT_CONTEXT_FORBIDDEN", failure.errorCode());
    }

    @Test
    void createFirstOwnerInvitationNeverReturnsTokenOrUrl() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenReturn(
            new FirstOwnerInvitationView(7L, 42L, "owner@example.invalid", "PENDING", expiresAt));
        CreateFirstOwnerInvitationHandler handler = new CreateFirstOwnerInvitationHandler(context);

        var response = handler.handle(invocation(
            new CreateFirstOwnerInvitationRequest(
                "owner@example.invalid",
                "en-US"),
            Map.of("tenantId", "42"),
            Optional.of("1001"),
            Optional.empty()));

        assertEquals(7L, response.invitationId());
        assertEquals("owner@example.invalid", response.inviteeEmail());
        ArgumentCaptor<CreateFirstOwnerInvitationCommand> command =
            ArgumentCaptor.forClass(CreateFirstOwnerInvitationCommand.class);
        verify(tenantAdministration).createFirstOwnerInvitation(command.capture());
        assertEquals(42L, command.getValue().tenantId());
        assertEquals("platform-identity:1001", command.getValue().platformOperatorRef());
        assertEquals("en-US", command.getValue().locale());
    }

    @Test
    void createFirstOwnerInvitationIgnoresCallerSuppliedInviteBaseUrl() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenReturn(
            new FirstOwnerInvitationView(7L, 42L, "owner@example.invalid", "PENDING", expiresAt));
        CreateFirstOwnerInvitationHandler handler = new CreateFirstOwnerInvitationHandler(context);

        handler.handle(rawCreateFirstOwnerInvocation(
            Map.of(
                "inviteeEmail", "owner@example.invalid",
                "inviteBaseUrl", "https://evil.example.invalid/invite",
                "locale", "en-US"),
            Map.of("tenantId", "42"),
            Optional.of("1001"),
            Optional.empty()));

        ArgumentCaptor<CreateFirstOwnerInvitationCommand> command =
            ArgumentCaptor.forClass(CreateFirstOwnerInvitationCommand.class);
        verify(tenantAdministration).createFirstOwnerInvitation(command.capture());
        assertEquals("owner@example.invalid", command.getValue().inviteeEmail());
        assertEquals("en-US", command.getValue().locale());
    }

    @Test
    void createFirstOwnerInvitationMapsOwnerConfigurationFailureToServiceUnavailable() {
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenThrow(
            new TenantAdministrationException(
                "FIRST_OWNER_INVITE_BASE_URL_NOT_CONFIGURED",
                "FIRST_OWNER invitation base URL is not configured"));
        CreateFirstOwnerInvitationHandler handler = new CreateFirstOwnerInvitationHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new CreateFirstOwnerInvitationRequest("owner@example.invalid", "en-US"),
                Map.of("tenantId", "42"),
                Optional.of("1001"),
                Optional.empty())));

        assertEquals(503, failure.status());
        assertEquals("FIRST_OWNER_INVITE_BASE_URL_NOT_CONFIGURED", failure.errorCode());
    }

    @Test
    void createFirstOwnerInvitationMapsMissingTenantToNotFound() {
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenThrow(
            new TenantAdministrationException("TENANT_NOT_FOUND", "Tenant not found"));
        CreateFirstOwnerInvitationHandler handler = new CreateFirstOwnerInvitationHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new CreateFirstOwnerInvitationRequest("owner@example.invalid", "en-US"),
                Map.of("tenantId", "42"),
                Optional.of("1001"),
                Optional.empty())));

        assertEquals(404, failure.status());
        assertEquals("TENANT_NOT_FOUND", failure.errorCode());
    }

    @Test
    void resendFirstOwnerInvitationDelegatesToTenantOwner() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        when(tenantAdministration.resendFirstOwnerInvitation(any())).thenReturn(
            new FirstOwnerInvitationView(8L, 42L, "owner@example.invalid", "PENDING", expiresAt));
        ResendFirstOwnerInvitationHandler handler = new ResendFirstOwnerInvitationHandler(context);

        handler.handle(invocation(
            new ResendFirstOwnerInvitationRequest("en-US"),
            Map.of("tenantId", "42"),
            Optional.of("1001"),
            Optional.empty()));

        ArgumentCaptor<ResendFirstOwnerInvitationCommand> command =
            ArgumentCaptor.forClass(ResendFirstOwnerInvitationCommand.class);
        verify(tenantAdministration).resendFirstOwnerInvitation(command.capture());
        assertEquals(42L, command.getValue().tenantId());
        assertEquals("platform-identity:1001", command.getValue().platformOperatorRef());
        assertEquals("en-US", command.getValue().locale());
    }

    @Test
    void resendFirstOwnerInvitationMapsOwnerFailure() {
        when(tenantAdministration.resendFirstOwnerInvitation(any())).thenThrow(
            new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_MISSING",
                "No pending FIRST_OWNER invitation exists"));
        ResendFirstOwnerInvitationHandler handler = new ResendFirstOwnerInvitationHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new ResendFirstOwnerInvitationRequest("en-US"),
                Map.of("tenantId", "42"),
                Optional.of("1001"),
                Optional.empty())));

        assertEquals(404, failure.status());
        assertEquals("FIRST_OWNER_INVITATION_MISSING", failure.errorCode());
    }

    @Test
    void revokeFirstOwnerInvitationDelegatesToTenantOwner() {
        RevokeFirstOwnerInvitationHandler handler = new RevokeFirstOwnerInvitationHandler(context);

        Void response = handler.handle(invocation(
            null,
            Map.of("tenantId", "42", "invitationId", "7"),
            Optional.of("1001"),
            Optional.empty()));

        assertNull(response);
        ArgumentCaptor<RevokeFirstOwnerInvitationCommand> command =
            ArgumentCaptor.forClass(RevokeFirstOwnerInvitationCommand.class);
        verify(tenantAdministration).revokeFirstOwnerInvitation(command.capture());
        assertEquals(42L, command.getValue().tenantId());
        assertEquals(7L, command.getValue().invitationId());
        assertEquals("platform-identity:1001", command.getValue().platformOperatorRef());
    }

    @Test
    void revokeFirstOwnerInvitationMapsOwnerFailure() {
        doThrow(new TenantAdministrationException(
            "FIRST_OWNER_INVITATION_NOT_REVOKABLE",
            "FIRST_OWNER invitation is not revokable"))
            .when(tenantAdministration).revokeFirstOwnerInvitation(any());
        RevokeFirstOwnerInvitationHandler handler = new RevokeFirstOwnerInvitationHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                null,
                Map.of("tenantId", "42", "invitationId", "7"),
                Optional.of("1001"),
                Optional.empty())));

        assertEquals(409, failure.status());
        assertEquals("FIRST_OWNER_INVITATION_NOT_REVOKABLE", failure.errorCode());
    }

    private static <T> EndpointInvocation<T> invocation(
            T body,
            Map<String, String> pathVariables,
            Optional<String> actorId,
            Optional<String> tenantId) {
        return new EndpointInvocation<>(
            body,
            pathVariables,
            Map.of(),
            Map.of(),
            tenantId,
            actorId,
            Optional.of("trace-1"),
            Instant.now().plusSeconds(30));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static EndpointInvocation<CreateFirstOwnerInvitationRequest> rawCreateFirstOwnerInvocation(
            Object body,
            Map<String, String> pathVariables,
            Optional<String> actorId,
            Optional<String> tenantId) {
        return new EndpointInvocation(
            body,
            pathVariables,
            Map.of(),
            Map.of(),
            tenantId,
            actorId,
            Optional.of("trace-1"),
            Instant.now().plusSeconds(30));
    }

    private record TestOperationalContext(TenantAdministration tenantAdministration) implements OperationalContext {

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
            if (!contractType.equals(TenantAdministration.class)) {
                throw new IllegalArgumentException("Unexpected contract type " + contractType.getName());
            }
            return contractType.cast(tenantAdministration);
        }
    }
}
