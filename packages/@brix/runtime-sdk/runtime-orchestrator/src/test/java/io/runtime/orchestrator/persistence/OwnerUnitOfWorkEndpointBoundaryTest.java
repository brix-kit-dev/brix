/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.TestEndpointAccessPolicies;
import io.runtime.sdk.plugin.EndpointInvocation;

class OwnerUnitOfWorkEndpointBoundaryTest {

    @Test
    void safeMethodsRunInAReadOnlyUnitOfWork() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        assertEquals("read", boundary.invoke(route("GET"), () -> "read"));
        assertEquals("head", boundary.invoke(route("HEAD"), () -> "head"));
        assertEquals(List.of("readOnly", "readOnly"), persistence.modes());
    }

    @Test
    void writeMethodsRunInAReadWriteUnitOfWork() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            boundary.invoke(route(method), () -> method);
        }
        assertEquals(List.of("required", "required", "required", "required"), persistence.modes());
    }

    @Test
    void modulesWithoutOwnerPersistenceAreInvokedDirectly() {
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(OwnerPersistenceProvider.none());

        assertEquals("direct", boundary.invoke(route("POST"), () -> "direct"));
    }

    @Test
    void ownerIsResolvedFromTheRouteModule() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary = new OwnerUnitOfWorkEndpointBoundary(
            owner -> "owner-plugin".equals(owner) ? Optional.of(persistence) : Optional.empty());

        boundary.invoke(new EndpointRoute("other-plugin", "other.read", "GET", "/api/other",
            TestEndpointAccessPolicies.authenticated("other:read"), invocation -> null), () -> null);
        assertTrue(persistence.modes().isEmpty());
    }

    @Test
    void aVerifiedTenantBindsThatTenantOnly() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        boundary.invoke(route("GET"), invocation("7"), () -> null);

        assertEquals(List.of(TenantScope.tenant(7L)), persistence.scopes());
        assertEquals(TenantScope.none(), TenantScopes.requested(), "the scope must not outlive the call");
    }

    @Test
    void entriesWithoutAVerifiedTenantReachNoTenantData() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        boundary.invoke(route("GET"), () -> null);
        boundary.invoke(route("GET"), invocation(null), () -> null);
        boundary.invoke(route("GET"), invocation("not-a-tenant"), () -> null);
        boundary.invoke(route("POST", TestEndpointAccessPolicies.anonymous("public-entry")), invocation("7"), () -> null);

        assertEquals(List.of(TenantScope.none(), TenantScope.none(), TenantScope.none(), TenantScope.none()),
            persistence.scopes());
    }

    @Test
    void aSourcePolicyEntryGetsItsRegisteredPolicy() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        boundary.invoke(route("POST", sourcePolicy("first-owner-invitation-resolution")), invocation("7"), () -> null);

        assertEquals(List.of(TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITATION_RESOLUTION, null)),
            persistence.scopes());
    }

    @Test
    void policiesKeyedByTheRuntimeCannotBeBoundByAnEndpoint() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        boundary.invoke(route("POST", sourcePolicy("login-tenant-discovery")), invocation(null), () -> null);
        boundary.invoke(route("POST", sourcePolicy("first-owner-invitee-setup-fanout")), invocation(null), () -> null);

        assertEquals(List.of(TenantScope.none(), TenantScope.none()), persistence.scopes());
    }

    private static EndpointRoute route(String method) {
        return route(method, TestEndpointAccessPolicies.authenticated("owner:read"));
    }

    private static EndpointRoute route(String method, EndpointAccessPolicy policy) {
        return new EndpointRoute("owner-plugin", "owner.entry", method, "/api/owner", policy, invocation -> null);
    }

    private static EndpointAccessPolicy sourcePolicy(String sourcePolicyRef) {
        return EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "anonymous"),
            Map.of("policyRef", "owner-token-entry"),
            Map.of("mode", "forbidden", "sourcePolicyRef", sourcePolicyRef));
    }

    private static EndpointInvocation<Object> invocation(String tenantId) {
        return new EndpointInvocation<>(null, Map.of(), Map.of(), Map.of(),
            Optional.ofNullable(tenantId), Optional.empty(), Optional.empty(), Instant.now().plusSeconds(5));
    }
}
