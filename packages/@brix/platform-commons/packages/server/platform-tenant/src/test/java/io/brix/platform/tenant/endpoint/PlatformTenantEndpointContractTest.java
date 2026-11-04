/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import io.brix.platform.tenant.internal.FirstOwnerAcceptanceResult;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointContract;
import io.runtime.orchestrator.endpoint.EndpointRequestInvalidException;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.ReplayableAnswers;
import io.runtime.orchestrator.endpoint.TenantGrantTable;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * platform-tenant's two endpoints through the dispatcher's typed path: their requests and answers fit the schemas
 * the manifest names (root BRX-RS-MANIFEST-022; standard-link plan L7.9), and the acceptance replays its answer
 * (point 10b-1).
 */
class PlatformTenantEndpointContractTest {

    private static final String SCHEMAS = "brix://schemas/platform-tenant/";
    private static final String TENANT_PROFILE_READ = "tenant:profile:read";

    @Test
    void theAcceptanceReplaysItsAnswerWhichTheRuntimeMayKeep() throws IOException {
        // BRX-TENANT-ENTRY-005; standard-link plan point 10b-1.
        ClassLoader classes = getClass().getClassLoader();
        try (InputStream manifest = classes.getResourceAsStream("META-INF/brix/plugin-manifest.yaml")) {
            PluginManifest.Route acceptance = new PluginManifestLoader().load(manifest).endpointDeclarations().stream()
                .filter(route -> "platform-tenant.first-owner.accept.v1".equals(route.getEndpointId()))
                .findFirst().orElseThrow();
            assertTrue(acceptance.replaysResults());
        }
        assertEquals(Optional.empty(), ReplayableAnswers.fieldNotKept(SCHEMAS + "first-owner-acceptance/1.0.0", classes));
    }

    @Test
    void theSessionTakesNoParametersAndItsAnswerFitsItsSchema() {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        dispatcher.replaceSnapshot(List.of(new EndpointRoute("platform-tenant", "platform-tenant.session.v1", "GET",
            "/api/tenant/session",
            EndpointAccessPolicy.fromDeclaration(
                Map.of("mode", "required", "policyRef", "tenant-actor"),
                Map.of("permissions", List.of(TENANT_PROFILE_READ), "decision", "all",
                    "resourcePolicyRef", "platform-tenant-session-read"),
                Map.of("mode", "required", "source", "runtime")),
            new TenantSessionHandler(),
            new EndpointContract(SCHEMAS + "tenant-session-request/1.0.0", SCHEMAS + "tenant-session/1.0.0", 200))),
            TenantGrantTable.build(List.of(new TenantGrantTable.PermissionDeclaration("platform-tenant",
                TENANT_PROFILE_READ, List.of(new TenantGrantTable.Grant("OWNER", "TENANT")))), List.of()));
        Map<String, List<String>> owner = headers(Map.of("x-actor-id", "2001", "x-tenant-id", "42",
            "x-auth-token-role", "actor", "x-auth-token-type", "access", "x-auth-token-scope", "actor",
            "x-auth-tenant-role-type", "OWNER", "x-auth-tenant-ref-id", "7"));

        EndpointResponse answer = (EndpointResponse) dispatcher.invoke("GET", "/api/tenant/session", null, Map.of(),
            owner);

        assertEquals(200, answer.status());
        JsonNode body = (JsonNode) answer.body();
        assertEquals("42", body.get("tenantId").asString());
        assertEquals("7", body.get("memberId").asString());
        assertEquals("OWNER", body.get("memberType").asString());
        assertEquals(TENANT_PROFILE_READ, body.get("permissions").get(0).asString());
        assertEquals("TENANT", body.get("scopes").get(TENANT_PROFILE_READ).asString());
        assertThrows(EndpointRequestInvalidException.class, () -> dispatcher.invoke("GET", "/api/tenant/session",
            null, Map.of("member", List.of("8")), owner));
    }

    @Test
    void theAcceptanceTakesOnlyTheInvitationTokenAndItsAnswerFitsItsSchema() {
        TenantAdministration administration = mock(TenantAdministration.class);
        when(administration.acceptFirstOwnerInvitation(any())).thenReturn(
            new FirstOwnerAcceptanceResult(42L, 500L, 600L, "ACTIVE"));
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        dispatcher.replaceSnapshot(List.of(new EndpointRoute("platform-tenant", "platform-tenant.first-owner.accept.v1",
            "POST", "/api/tenant/first-owner-invitations/accept",
            EndpointAccessPolicy.fromDeclaration(
                Map.of("mode", "required"),
                Map.of("policyRef", "tenant:first-owner:accept"),
                Map.of("mode", "forbidden", "sourcePolicyRef", "first-owner-invitation-resolution")),
            new AcceptFirstOwnerInvitationHandler(() -> administration),
            new EndpointContract(SCHEMAS + "accept-first-owner-invitation-request/1.0.0",
                SCHEMAS + "first-owner-acceptance/1.0.0", 200))));
        Map<String, List<String>> invitee = headers(Map.of("x-actor-id", "900", "x-auth-token-role", "actor",
            "x-auth-token-type", "identity", "x-auth-allowed-action",
            AcceptFirstOwnerInvitationHandler.FIRST_OWNER_ACCEPT_ACTION));

        EndpointResponse answer = (EndpointResponse) dispatcher.invoke("POST",
            "/api/tenant/first-owner-invitations/accept", Map.of("invitationToken", "raw-invite-token"), Map.of(),
            invitee);

        assertEquals(200, answer.status());
        JsonNode body = (JsonNode) answer.body();
        assertEquals("42", body.get("tenantId").asString(), "ids are strings, as the BFF reads them");
        assertEquals("600", body.get("profileId").asString());
        assertEquals("ACTIVE", body.get("tenantStatus").asString());
        for (Map<String, Object> broken : List.<Map<String, Object>>of(Map.of("invitationToken", ""),
                Map.of("token", "raw-invite-token"), Map.of())) {
            assertThrows(EndpointRequestInvalidException.class, () -> dispatcher.invoke("POST",
                "/api/tenant/first-owner-invitations/accept", broken, Map.of(), invitee), broken.toString());
        }
    }

    private static Map<String, List<String>> headers(Map<String, String> values) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        values.forEach((name, value) -> headers.put(name, List.of(value)));
        return headers;
    }
}
