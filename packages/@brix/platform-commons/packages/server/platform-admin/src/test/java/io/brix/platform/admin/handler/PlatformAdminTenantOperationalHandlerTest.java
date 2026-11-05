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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;

import io.brix.platform.admin.dto.CreatePlatformTenantRequest;
import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.audit.internal.PlatformAuditAdministration;
import io.brix.platform.audit.internal.RecordPlatformAuditCommand;
import io.brix.platform.tenant.internal.CreateFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.CreatePendingTenantCommand;
import io.brix.platform.tenant.internal.FirstOwnerInvitationView;
import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.RevokeFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.brix.platform.tenant.internal.TenantAdministrationTenant;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.EndpointContract;
import io.runtime.orchestrator.endpoint.EndpointRequestInvalidException;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.TypedEndpointRules;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.operational.OperationalModuleDescriptorLoader;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.RuntimeOperationalView;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;

class PlatformAdminTenantOperationalHandlerTest {

    private static final String CREATE = "platform.tenants.create.v1";
    private static final String INVITE = "platform.tenants.first-owner-invitations.create.v1";

    private final TenantAdministration tenantAdministration = org.mockito.Mockito.mock(TenantAdministration.class);
    private final PlatformAuditAdministration audit = org.mockito.Mockito.mock(PlatformAuditAdministration.class);
    private final OperationalContext context = new TestOperationalContext(tenantAdministration, audit);

    @Test
    void createTenantDelegatesToTenantAdministrationWithoutOwnerDataAccess() {
        when(tenantAdministration.createPendingTenant(any())).thenReturn(
            new TenantAdministrationTenant(42L, "acme", "Acme", "PENDING_ACTIVATION"));
        CreatePlatformTenantHandler handler = new CreatePlatformTenantHandler(context);

        var response = handler.handle(invocation(
            new CreatePlatformTenantRequest("acme", "Acme"),
            Map.of(),
            Optional.of("1001"),
            Optional.empty())).toCompletableFuture().join();

        assertEquals(42L, response.tenantId());
        assertEquals("PENDING_ACTIVATION", response.status());
        ArgumentCaptor<CreatePendingTenantCommand> command =
            ArgumentCaptor.forClass(CreatePendingTenantCommand.class);
        verify(tenantAdministration).createPendingTenant(command.capture());
        assertEquals("acme", command.getValue().code());
        assertEquals(1001L, command.getValue().platformAdminIdentityId());
    }

    @Test
    void aNewTenantIsAnsweredAsItsSchemaSays() {
        // The real descriptor and schemas, through the dispatcher (idempotency design D78; standard-link plan point 10b-3).
        when(tenantAdministration.createPendingTenant(any())).thenReturn(
            new TenantAdministrationTenant(42L, "acme", "Acme", "PENDING_ACTIVATION"));

        EndpointResponse answer = (EndpointResponse) create(Map.of("code", "acme", "name", "Acme"));

        assertEquals(200, answer.status());
        JsonNode body = (JsonNode) answer.body();
        assertEquals("42", body.get("tenantId").asString());
        assertEquals("acme", body.get("code").asString());
        assertEquals("PENDING_ACTIVATION", body.get("status").asString());
    }

    @ParameterizedTest
    @MethodSource("requestsTheSchemaRefuses")
    void aRequestTheSchemaRefusesNeverReachesTheOwner(Map<String, Object> request) {
        assertThrows(EndpointRequestInvalidException.class, () -> create(request));

        verify(tenantAdministration, never()).createPendingTenant(any());
    }

    static Stream<Map<String, Object>> requestsTheSchemaRefuses() {
        return Stream.of(
            Map.of("code", "acme"),
            Map.of("name", "Acme"),
            Map.of("code", "a", "name", "Acme"),
            Map.of("code", "Acme", "name", "Acme"),
            Map.of("code", "acme_1", "name", "Acme"),
            Map.of("code", "acme", "name", "   "),
            Map.of("code", "acme", "name", "Acme", "ownerIdentityId", 7));
    }

    @Test
    void theTenantCreationIsReplayedThroughTheTenantOwner() {
        // Super-admin §6: required. The record goes with the one write through the tenant owner's contract.
        EndpointContract contract = endpoint(CREATE).contract();

        assertEquals("brix.internal.tenant.administration", contract.replay().writeContract());
        assertEquals(Optional.empty(), TypedEndpointRules.problem(new CreatePlatformTenantHandler(context),
            contract, getClass().getClassLoader()));
    }

    @Test
    void aFirstOwnerInvitationIsAnsweredAsItsSchemaSays() {
        // Standard-link plan point 10b-4: the real descriptor and schemas, through the dispatcher.
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenReturn(new FirstOwnerInvitationView(
            7L, 42L, "owner@example.invalid", "PENDING", OffsetDateTime.parse("2026-07-28T12:00:00Z")));

        EndpointResponse answer = (EndpointResponse) invite(Map.of("inviteeEmail", "owner@example.invalid"));

        assertEquals(200, answer.status());
        JsonNode body = (JsonNode) answer.body();
        assertEquals("7", body.get("invitationId").asString());
        assertEquals("42", body.get("tenantId").asString());
        assertEquals("PENDING", body.get("status").asString());
    }

    @ParameterizedTest
    @MethodSource("invitationsTheSchemaRefuses")
    void anInvitationTheSchemaRefusesNeverReachesTheOwner(Map<String, Object> request) {
        assertThrows(EndpointRequestInvalidException.class, () -> invite(request));

        verify(tenantAdministration, never()).createFirstOwnerInvitation(any());
    }

    static Stream<Map<String, Object>> invitationsTheSchemaRefuses() {
        return Stream.of(
            Map.of(),
            Map.of("inviteeEmail", ""),
            Map.of("inviteeEmail", "not-an-address"),
            Map.of("inviteeEmail", "owner@example.invalid", "locale", 7),
            // The caller does not choose where the invitation's link leads.
            Map.of("inviteeEmail", "owner@example.invalid", "inviteBaseUrl", "https://evil.example.invalid/invite"));
    }

    @Test
    void theInvitationIsReplayedThroughTheTenantOwner() {
        EndpointContract contract = endpoint(INVITE).contract();

        assertEquals("brix.internal.tenant.administration", contract.replay().writeContract());
        assertEquals(Optional.empty(), TypedEndpointRules.problem(new CreateFirstOwnerInvitationHandler(context),
            contract, getClass().getClassLoader()));
    }

    private Object create(Map<String, Object> request) {
        return call(CREATE, new CreatePlatformTenantHandler(context), "/api/platform/tenants", request,
            "platform:tenant:create");
    }

    private Object invite(Map<String, Object> request) {
        return call(INVITE, new CreateFirstOwnerInvitationHandler(context),
            "/api/platform/tenants/42/first-owner-invitations", request, "platform:tenant:first-owner-invite");
    }

    private Object call(String endpointId, EndpointHandler<?, ?> handler, String path, Map<String, Object> request,
            String permission) {
        OperationalModuleDescriptor.EndpointDeclaration endpoint = endpoint(endpointId);
        EndpointContract contract = endpoint.contract();
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        dispatcher.replaceSnapshot(List.of(new EndpointRoute("platform-admin", endpoint.endpointId(), "POST",
            endpoint.path(), endpoint.accessPolicy(), handler,
            new EndpointContract(contract.requestSchemaRef(), contract.responseSchemaRef(), contract.status(),
                contract.timeoutPolicyRef()))));
        return dispatcher.invoke("POST", path, request, Map.of(), Map.of(
            "x-actor-id", List.of("1001"),
            "x-auth-token-role", List.of("platform-admin"),
            "x-auth-token-type", List.of("access"),
            "x-auth-token-scope", List.of("PLATFORM"),
            "x-auth-permission", List.of(permission)));
    }

    private OperationalModuleDescriptor.EndpointDeclaration endpoint(String endpointId) {
        return new OperationalModuleDescriptorLoader()
            .load(getClass().getClassLoader().getResource(OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE))
            .endpoints().get(endpointId);
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
            Optional.empty())).toCompletableFuture().join();

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
    void createFirstOwnerInvitationMapsInviteeSetupConfigurationFailureToServiceUnavailable() {
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenThrow(
            new TenantAdministrationException(
                "FIRST_OWNER_SETUP_BASE_URL_NOT_CONFIGURED",
                "FIRST_OWNER setup base URL is not configured"));
        CreateFirstOwnerInvitationHandler handler = new CreateFirstOwnerInvitationHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new CreateFirstOwnerInvitationRequest("owner@example.invalid", "en-US"),
                Map.of("tenantId", "42"),
                Optional.of("1001"),
                Optional.empty())));

        assertEquals(503, failure.status());
        assertEquals("FIRST_OWNER_SETUP_BASE_URL_NOT_CONFIGURED", failure.errorCode());
    }

    @Test
    void createFirstOwnerInvitationMapsInviteeIdentityEligibilityFailureToConflict() {
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenThrow(
            new TenantAdministrationException(
                "FIRST_OWNER_INVITEE_IDENTITY_NOT_ELIGIBLE",
                "FIRST_OWNER invitee identity is not eligible for setup"));
        CreateFirstOwnerInvitationHandler handler = new CreateFirstOwnerInvitationHandler(context);

        EndpointHandlingException failure = assertThrows(
            EndpointHandlingException.class,
            () -> handler.handle(invocation(
                new CreateFirstOwnerInvitationRequest("owner@example.invalid", "en-US"),
                Map.of("tenantId", "42"),
                Optional.of("1001"),
                Optional.empty())));

        assertEquals(409, failure.status());
        assertEquals("FIRST_OWNER_INVITEE_IDENTITY_NOT_ELIGIBLE", failure.errorCode());
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

    @Test
    void aCreatedTenantIsWrittenToThePlatformAuditAfterTheOwnerCommitted() {
        when(tenantAdministration.createPendingTenant(any())).thenReturn(
            new TenantAdministrationTenant(42L, "acme", "Acme", "PENDING_ACTIVATION"));

        new CreatePlatformTenantHandler(context).handle(invocation(
            new CreatePlatformTenantRequest("acme", "Acme"), Map.of(), Optional.of("1001"), Optional.empty()));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(tenantAdministration, audit);
        order.verify(tenantAdministration).createPendingTenant(any());
        RecordPlatformAuditCommand record = recorded(order);
        assertEquals(PlatformAuditActions.TENANT_CREATED, record.action());
        assertEquals(1001L, record.actorIdentityId());
        assertEquals("platform-admin", record.executionIdentity());
        assertEquals("TENANT", record.targetType());
        assertEquals("42", record.targetId());
        assertEquals(java.util.List.of(42L), record.affectedTenants());
        assertEquals(true, record.success());
        assertEquals("platform-operation", record.reasonCode());
        assertEquals(null, record.errorCode());
        org.junit.jupiter.api.Assertions.assertNotNull(record.traceId(), "a short trace id is replaced by a correlation id");
    }

    @Test
    void aFailedInvitationIsRecordedAsAFailureAndItsOwnErrorIsReturned() {
        when(tenantAdministration.createFirstOwnerInvitation(any())).thenThrow(
            new TenantAdministrationException("TENANT_NOT_FOUND", "Tenant not found"));

        EndpointHandlingException failure = assertThrows(EndpointHandlingException.class,
            () -> new CreateFirstOwnerInvitationHandler(context).handle(invocation(
                new CreateFirstOwnerInvitationRequest("owner@example.invalid", "en-US"),
                Map.of("tenantId", "42"), Optional.of("1001"), Optional.empty())));

        assertEquals("TENANT_NOT_FOUND", failure.errorCode());
        RecordPlatformAuditCommand record = recorded(org.mockito.Mockito.inOrder(audit));
        assertEquals(PlatformAuditActions.FIRST_OWNER_INVITATION_CREATED, record.action());
        assertEquals(false, record.success());
        assertEquals("tenant_not_found", record.errorCode());
        assertEquals("INVITATION", record.targetType());
        assertEquals(null, record.targetId());
        assertEquals(java.util.List.of(42L), record.affectedTenants());
    }

    @Test
    void whenTheRecordCannotBeWrittenTheRequestFailsButTheOwnersChangeStays() {
        when(tenantAdministration.createPendingTenant(any())).thenReturn(
            new TenantAdministrationTenant(42L, "acme", "Acme", "PENDING_ACTIVATION"));
        when(audit.record(any())).thenThrow(new IllegalStateException("platform audit unavailable"));

        EndpointHandlingException failure = assertThrows(EndpointHandlingException.class,
            () -> new CreatePlatformTenantHandler(context).handle(invocation(
                new CreatePlatformTenantRequest("acme", "Acme"), Map.of(), Optional.of("1001"), Optional.empty())));

        assertEquals(503, failure.status());
        assertEquals("PLATFORM_AUDIT_WRITE_FAILED", failure.errorCode());
        verify(tenantAdministration).createPendingTenant(any());
    }

    @Test
    void whenAFailedOperationCannotBeRecordedItsOwnErrorIsStillReturned() {
        doThrow(new TenantAdministrationException("FIRST_OWNER_INVITATION_NOT_REVOKABLE", "not revokable"))
            .when(tenantAdministration).revokeFirstOwnerInvitation(any());
        IllegalStateException unavailable = new IllegalStateException("platform audit unavailable");
        when(audit.record(any())).thenThrow(unavailable);

        EndpointHandlingException failure = assertThrows(EndpointHandlingException.class,
            () -> new RevokeFirstOwnerInvitationHandler(context).handle(invocation(
                null, Map.of("tenantId", "42", "invitationId", "7"), Optional.of("1001"), Optional.empty())));

        assertEquals("FIRST_OWNER_INVITATION_NOT_REVOKABLE", failure.errorCode());
        assertEquals(unavailable, failure.getSuppressed()[0]);
    }

    @Test
    void revokingAndResendingAreRecordedWithTheInvitation() {
        when(tenantAdministration.resendFirstOwnerInvitation(any())).thenReturn(
            new FirstOwnerInvitationView(9L, 42L, "owner@example.invalid", "PENDING", OffsetDateTime.now().plusDays(7)));

        new RevokeFirstOwnerInvitationHandler(context).handle(invocation(
            null, Map.of("tenantId", "42", "invitationId", "7"), Optional.of("1001"), Optional.empty()));
        new ResendFirstOwnerInvitationHandler(context).handle(invocation(
            new ResendFirstOwnerInvitationRequest("en-US"), Map.of("tenantId", "42"), Optional.of("1001"), Optional.empty()));

        ArgumentCaptor<RecordPlatformAuditCommand> records = ArgumentCaptor.forClass(RecordPlatformAuditCommand.class);
        verify(audit, org.mockito.Mockito.times(2)).record(records.capture());
        assertEquals(PlatformAuditActions.FIRST_OWNER_INVITATION_REVOKED, records.getAllValues().get(0).action());
        assertEquals("7", records.getAllValues().get(0).targetId());
        assertEquals(PlatformAuditActions.FIRST_OWNER_INVITATION_RESENT, records.getAllValues().get(1).action());
        assertEquals("9", records.getAllValues().get(1).targetId());
    }

    @Test
    void aRequestRefusedBeforeTheOperationIsNotRecorded() {
        assertThrows(EndpointHandlingException.class, () -> new CreatePlatformTenantHandler(context).handle(invocation(
            new CreatePlatformTenantRequest("acme", "Acme"), Map.of(), Optional.of("1001"), Optional.of("tenant-a"))));

        org.mockito.Mockito.verifyNoInteractions(audit);
    }

    private RecordPlatformAuditCommand recorded(org.mockito.InOrder order) {
        ArgumentCaptor<RecordPlatformAuditCommand> record = ArgumentCaptor.forClass(RecordPlatformAuditCommand.class);
        order.verify(audit).record(record.capture());
        return record.getValue();
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

    private record TestOperationalContext(TenantAdministration tenantAdministration, PlatformAuditAdministration audit)
            implements OperationalContext {

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
            if (contractType.equals(PlatformAuditAdministration.class)) {
                return contractType.cast(audit);
            }
            if (!contractType.equals(TenantAdministration.class)) {
                throw new IllegalArgumentException("Unexpected contract type " + contractType.getName());
            }
            return contractType.cast(tenantAdministration);
        }
    }
}
