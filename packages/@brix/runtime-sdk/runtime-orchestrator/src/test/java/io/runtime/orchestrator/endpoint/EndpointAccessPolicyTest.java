/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EndpointAccessPolicyTest {

    private static final Map<String, Object> PLATFORM_AUTHENTICATION =
        Map.of("mode", "required", "policyRef", "platform-token");
    private static final Map<String, Object> FORBIDDEN_TENANT = Map.of("mode", "forbidden");

    @Test
    void parsesPermissionsSeparatelyFromNamedPolicyReferences() {
        EndpointAccessPolicy policy = EndpointAccessPolicy.fromDeclaration(
            PLATFORM_AUTHENTICATION,
            Map.of(
                "permissions", List.of("platform:tenant:create", "platform:tenant:read"),
                "decision", "any",
                "policyRef", "tenant-directory",
                "resourcePolicyRef", "tenant-directory-write"),
            FORBIDDEN_TENANT);

        assertEquals(EndpointAccessPolicy.AuthenticationMode.REQUIRED, policy.authentication());
        assertEquals(EndpointAccessPolicy.TokenPolicy.PLATFORM_TOKEN, policy.tokenPolicy());
        assertEquals(Set.of("platform:tenant:create", "platform:tenant:read"), policy.permissions());
        assertEquals(EndpointAccessPolicy.PermissionDecision.ANY, policy.decision());
        assertEquals("tenant-directory-write", policy.authorizationPolicyRef());
        assertEquals(EndpointAccessPolicy.TenantContextMode.FORBIDDEN, policy.tenantContext());
        assertTrue(policy.requiresVerifiedPrincipal());
    }

    @Test
    void policyRefIsNeverPromotedToPermission() {
        EndpointAccessPolicy policy = EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "anonymous"),
            Map.of("policyRef", "platform:tenant:create"),
            FORBIDDEN_TENANT);

        assertTrue(policy.permissions().isEmpty());
        assertEquals("platform:tenant:create", policy.authorizationPolicyRef());
        assertEquals(EndpointAccessPolicy.TokenPolicy.ANY_VERIFIED, policy.tokenPolicy());
        assertFalse(policy.requiresVerifiedPrincipal());
    }

    @Test
    void decisionDefaultsToAll() {
        EndpointAccessPolicy policy = EndpointAccessPolicy.fromDeclaration(
            PLATFORM_AUTHENTICATION,
            Map.of("permissions", List.of("platform:admin:read")),
            FORBIDDEN_TENANT);

        assertEquals(EndpointAccessPolicy.PermissionDecision.ALL, policy.decision());
    }

    @Test
    void ownerVerifiedCredentialModesAreKnownButNotRuntimeAuthenticated() {
        for (String mode : List.of("setup-token", "bootstrap-setup")) {
            EndpointAccessPolicy policy = EndpointAccessPolicy.fromDeclaration(
                Map.of("mode", mode),
                Map.of("policyRef", "setup:validate"),
                FORBIDDEN_TENANT);

            assertFalse(policy.requiresVerifiedPrincipal());
        }
    }

    @Test
    void rejectsMissingOrUnknownDeclarations() {
        assertRejected(Map.of(), Map.of("policyRef", "x"), FORBIDDEN_TENANT);
        assertRejected(null, Map.of("policyRef", "x"), FORBIDDEN_TENANT);
        assertRejected(Map.of("mode", "requried"), Map.of("policyRef", "x"), FORBIDDEN_TENANT);
        assertRejected(Map.of("mode", List.of("required")), Map.of("policyRef", "x"), FORBIDDEN_TENANT);
        assertRejected(Map.of("mode", "required", "policyRef", "interactive-read"),
            Map.of("permissions", List.of("p")), FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION, Map.of("permissions", List.of("p"), "decision", "most"),
            FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION, Map.of("permissions", List.of("p")), Map.of());
        assertRejected(PLATFORM_AUTHENTICATION, Map.of("permissions", List.of("p")), Map.of("mode", "shared"));
    }

    @Test
    void rejectsMalformedPermissions() {
        assertRejected(PLATFORM_AUTHENTICATION, Map.of("permissions", "platform:tenant:create"), FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION, Map.of("permissions", List.of(" ")), FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION, Map.of("permissions", List.of(7)), FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION, Map.of("decision", "all"), FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION, null, FORBIDDEN_TENANT);
    }

    @Test
    void rejectsStepUpDeclarationsTheDispatcherCannotEnforceYet() {
        assertRejected(Map.of("mode", "required", "policyRef", "platform-token", "acr", "brix:aal2-phr"),
            Map.of("permissions", List.of("platform:admin:create")), FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION,
            Map.of("permissions", List.of("platform:admin:create"), "max_age", "300"), FORBIDDEN_TENANT);
        assertRejected(PLATFORM_AUTHENTICATION,
            Map.of("permissions", List.of("platform:admin:create"), "maxAge", "PT5M"), FORBIDDEN_TENANT);
    }

    @Test
    void rejectsPrincipalRequirementsOnEndpointsWithoutRuntimeAuthentication() {
        assertRejected(Map.of("mode", "anonymous", "policyRef", "platform-token"),
            Map.of("policyRef", "x"), FORBIDDEN_TENANT);
        assertRejected(Map.of("mode", "anonymous"),
            Map.of("permissions", List.of("platform:tenant:read")), FORBIDDEN_TENANT);
        assertRejected(Map.of("mode", "setup-token"),
            Map.of("permissions", List.of("platform:tenant:read")), FORBIDDEN_TENANT);
        assertRejected(Map.of("mode", "anonymous"), Map.of("policyRef", "x"), Map.of("mode", "required"));
    }

    @Test
    void tokenPoliciesAcceptOnlyTheirTokenClass() {
        EndpointAccessPolicy.TokenPolicy platform = EndpointAccessPolicy.TokenPolicy.PLATFORM_TOKEN;
        assertTrue(platform.accepts("platform-admin", "access", "PLATFORM"));
        assertFalse(platform.accepts("platform-admin", "mfa_challenge", "PLATFORM"));
        assertFalse(platform.accepts("platform-admin", "access", null));
        assertFalse(platform.accepts("bootstrap", "BOOTSTRAP_SETUP", "BOOTSTRAP"));
        assertFalse(platform.accepts("actor", "access", "actor"));
        assertFalse(platform.accepts(null, null, null));

        EndpointAccessPolicy.TokenPolicy user = EndpointAccessPolicy.TokenPolicy.AUTHENTICATED_USER;
        assertTrue(user.accepts("actor", "access", "actor"));
        assertTrue(user.accepts("subject", "access", "subject"));
        assertFalse(user.accepts("actor", "access", "subject"));
        assertFalse(user.accepts("actor", "identity", "identity"));
        assertFalse(user.accepts("platform-admin", "access", "PLATFORM"));
        assertFalse(user.accepts(null, "access", null));

        assertTrue(EndpointAccessPolicy.TokenPolicy.ANY_VERIFIED.accepts(null, null, null));
    }

    @Test
    void tenantTokenPoliciesSeparateManagementAndEndUserApis() {
        EndpointAccessPolicy.TokenPolicy actor = EndpointAccessPolicy.TokenPolicy.TENANT_ACTOR;
        assertTrue(actor.accepts("actor", "access", "actor"));
        assertFalse(actor.accepts("subject", "access", "subject"));
        assertFalse(actor.accepts("actor", "identity", "identity"));
        assertFalse(actor.accepts("actor", "access", "subject"));
        assertFalse(actor.accepts("platform-admin", "access", "PLATFORM"));
        assertEquals(java.util.Optional.of("actor"), actor.tenantTokenRole());

        EndpointAccessPolicy.TokenPolicy subject = EndpointAccessPolicy.TokenPolicy.TENANT_SUBJECT;
        assertTrue(subject.accepts("subject", "access", "subject"));
        assertFalse(subject.accepts("actor", "access", "actor"));
        assertFalse(subject.accepts(null, null, null));
        assertEquals(java.util.Optional.of("subject"), subject.tenantTokenRole());

        assertTrue(EndpointAccessPolicy.TokenPolicy.AUTHENTICATED_USER.tenantTokenRole().isEmpty());
        assertTrue(EndpointAccessPolicy.TokenPolicy.PLATFORM_TOKEN.tenantTokenRole().isEmpty());
    }

    @Test
    void requiredTenantContextNeedsATenantTokenPolicy() {
        Map<String, Object> permission = Map.of("permissions", List.of("example:order:read"));
        Map<String, Object> requiredTenant = Map.of("mode", "required");
        assertRejected(Map.of("mode", "required", "policyRef", "authenticated-user"), permission, requiredTenant);
        assertRejected(Map.of("mode", "required", "policyRef", "platform-token"), permission, requiredTenant);
        assertRejected(Map.of("mode", "required"), permission, requiredTenant);

        EndpointAccessPolicy actor = EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "tenant-actor"), permission, requiredTenant);
        EndpointAccessPolicy subject = EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "tenant-subject"), permission, requiredTenant);
        assertEquals(EndpointAccessPolicy.TokenPolicy.TENANT_ACTOR, actor.tokenPolicy());
        assertEquals(EndpointAccessPolicy.TokenPolicy.TENANT_SUBJECT, subject.tokenPolicy());
    }

    private static void assertRejected(
            Map<String, ?> authentication,
            Map<String, ?> authorization,
            Map<String, ?> tenantContext) {
        assertThrows(
            IllegalArgumentException.class,
            () -> EndpointAccessPolicy.fromDeclaration(authentication, authorization, tenantContext));
    }
}
