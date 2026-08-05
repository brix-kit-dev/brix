/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.tenant.internal.AcceptFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.FirstOwnerAcceptanceResult;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class AcceptFirstOwnerInvitationHandlerTest {

    private final TenantAdministration tenantAdministration =
        org.mockito.Mockito.mock(TenantAdministration.class);
    private final AcceptFirstOwnerInvitationHandler handler =
        new AcceptFirstOwnerInvitationHandler(() -> tenantAdministration);

    @Test
    void delegatesToTenantOwnerWithVerifiedIdentityToken() {
        when(tenantAdministration.acceptFirstOwnerInvitation(any())).thenReturn(
            new FirstOwnerAcceptanceResult(42L, 500L, 600L, "ACTIVE"));

        FirstOwnerAcceptanceDto response = handler.handle(invocation(
            new AcceptFirstOwnerInvitationRequest("raw-invite-token"),
            Optional.of("900"),
            Optional.empty(),
            "actor",
            "identity",
            Set.of(AcceptFirstOwnerInvitationHandler.FIRST_OWNER_ACCEPT_ACTION)));

        assertEquals(42L, response.tenantId());
        assertEquals(500L, response.memberId());
        assertEquals("ACTIVE", response.tenantStatus());
        ArgumentCaptor<AcceptFirstOwnerInvitationCommand> command =
            ArgumentCaptor.forClass(AcceptFirstOwnerInvitationCommand.class);
        verify(tenantAdministration).acceptFirstOwnerInvitation(command.capture());
        assertEquals("raw-invite-token", command.getValue().invitationToken());
        assertEquals(900L, command.getValue().identityId());
    }

    @Test
    void rejectsPlatformToken() {
        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new AcceptFirstOwnerInvitationRequest("raw-invite-token"),
            Optional.of("900"),
            Optional.empty(),
            "platform-admin",
            "access",
                Set.of())));

        assertEquals(403, failure.status());
        assertEquals("FIRST_OWNER_ACTOR_IDENTITY_REQUIRED", failure.errorCode());
    }

    @Test
    void rejectsCallerTenantContext() {
        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new AcceptFirstOwnerInvitationRequest("raw-invite-token"),
            Optional.of("900"),
            Optional.of("42"),
            "actor",
            "identity",
                Set.of(AcceptFirstOwnerInvitationHandler.FIRST_OWNER_ACCEPT_ACTION))));

        assertEquals(403, failure.status());
        assertEquals("FIRST_OWNER_TENANT_CONTEXT_FORBIDDEN", failure.errorCode());
    }

    @Test
    void mapsInvalidInvitationToNotFound() {
        when(tenantAdministration.acceptFirstOwnerInvitation(any())).thenThrow(
            new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_INVALID",
                "FIRST_OWNER invitation is invalid"));

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new AcceptFirstOwnerInvitationRequest("raw-invite-token"),
            Optional.of("900"),
            Optional.empty(),
            "actor",
            "identity",
                Set.of(AcceptFirstOwnerInvitationHandler.FIRST_OWNER_ACCEPT_ACTION))));

        assertEquals(404, failure.status());
        assertEquals("FIRST_OWNER_INVITATION_INVALID", failure.errorCode());
    }

    private static EndpointInvocation<AcceptFirstOwnerInvitationRequest> invocation(
            AcceptFirstOwnerInvitationRequest body,
            Optional<String> actorId,
            Optional<String> tenantId,
            String tokenRole,
            String tokenType,
            Set<String> allowedActions) {
        return new EndpointInvocation<>(
            body,
            Map.of(),
            Map.of(),
            headers(tokenRole, tokenType, allowedActions),
            tenantId,
            actorId,
            Optional.of("trace-1"),
            Instant.now().plusSeconds(30));
    }

    private static Map<String, List<String>> headers(
            String tokenRole,
            String tokenType,
            Set<String> allowedActions) {
        return Map.of(
            "x-auth-token-role", List.of(tokenRole),
            "x-auth-token-type", List.of(tokenType),
            "x-auth-allowed-action", List.copyOf(allowedActions));
    }
}
