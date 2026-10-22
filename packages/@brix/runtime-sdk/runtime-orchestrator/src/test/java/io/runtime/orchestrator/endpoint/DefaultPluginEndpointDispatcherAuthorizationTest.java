/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Dispatcher authorization step: every rejection happens before the handler.
 *
 * <p>Caller header maps mirror what {@link RuntimeShellEndpointController}
 * forwards for each token class issued by the platform auth adapter.</p>
 */
class DefaultPluginEndpointDispatcherAuthorizationTest {

    private static final String TENANT_CREATE = "platform:tenant:create";
    private static final String TENANT_READ = "platform:tenant:read";

    private final RecordingHandler handler = new RecordingHandler();
    private final DefaultPluginEndpointDispatcher dispatcher =
        new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));

    @Test
    void platformEndpointRejectsMissingPrincipalBeforeHandler() {
        publish(TestEndpointAccessPolicies.platform(TENANT_CREATE));

        assertDenied(401, EndpointAccessDeniedException.AUTHENTICATION_REQUIRED, Map.of());
    }

    @Test
    void platformEndpointAcceptsPlatformTokenHoldingTheDeclaredPermission() {
        publish(TestEndpointAccessPolicies.platform(TENANT_CREATE));

        assertEquals("handled", invoke(platformToken(TENANT_READ, TENANT_CREATE)));
        assertEquals(1, handler.invocations.size());
        assertEquals(Optional.of("1001"), handler.invocations.get(0).actorId());
    }

    @Test
    void platformEndpointRejectsPlatformTokenWithoutTheDeclaredPermission() {
        publish(TestEndpointAccessPolicies.platform(TENANT_CREATE));

        assertDenied(403, EndpointAccessDeniedException.PERMISSION_DENIED, platformToken(TENANT_READ));
        assertDenied(403, EndpointAccessDeniedException.PERMISSION_DENIED, platformToken());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tokensOfOtherClassesHoldingThePermission")
    void platformEndpointRejectsTokensOfOtherClassesBeforeHandler(
            String tokenClass,
            Map<String, List<String>> caller) {
        publish(TestEndpointAccessPolicies.platform(TENANT_CREATE));

        assertDenied(403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED, caller);
    }

    static Stream<Arguments> tokensOfOtherClassesHoldingThePermission() {
        return Stream.of(
            Arguments.of("platform MFA challenge token",
                caller("1001", null, "platform-admin", "mfa_challenge", "PLATFORM", TENANT_CREATE)),
            Arguments.of("bootstrap setup token",
                caller("1001", null, "bootstrap", "BOOTSTRAP_SETUP", "BOOTSTRAP", TENANT_CREATE)),
            Arguments.of("identity token",
                caller("1001", null, "actor", "identity", "identity", TENANT_CREATE)),
            Arguments.of("actor access token of a tenant context",
                caller("1001", "42", "actor", "access", "actor", TENANT_CREATE)),
            Arguments.of("subject access token of a tenant context",
                caller("1001", "42", "subject", "access", "subject", TENANT_CREATE)),
            Arguments.of("platform role without platform scope",
                caller("1001", null, "platform-admin", "access", null, TENANT_CREATE)),
            Arguments.of("principal without token attributes",
                caller("1001", null, null, null, null, TENANT_CREATE)));
    }

    @Test
    void platformEndpointRejectsPlatformTokenCarryingTenantContext() {
        publish(TestEndpointAccessPolicies.platform(TENANT_CREATE));

        assertDenied(403, EndpointAccessDeniedException.TENANT_CONTEXT_FORBIDDEN,
            caller("1001", "42", "platform-admin", "access", "PLATFORM", TENANT_CREATE));
    }

    @Test
    void permissionHeaderWithoutVerifiedPrincipalGrantsNothing() {
        publish(TestEndpointAccessPolicies.platform(TENANT_CREATE));

        assertDenied(401, EndpointAccessDeniedException.AUTHENTICATION_REQUIRED, Map.of(
            "x-auth-token-role", List.of("platform-admin"),
            "x-auth-token-type", List.of("access"),
            "x-auth-token-scope", List.of("PLATFORM"),
            "x-auth-permission", List.of(TENANT_CREATE)));
    }

    @Test
    void decisionAllRequiresEveryPermissionAndDecisionAnyRequiresOne() {
        publish(EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "platform-token"),
            Map.of("permissions", List.of(TENANT_CREATE, TENANT_READ), "decision", "all"),
            Map.of("mode", "forbidden")));
        assertDenied(403, EndpointAccessDeniedException.PERMISSION_DENIED, platformToken(TENANT_READ));
        assertEquals("handled", invoke(platformToken(TENANT_READ, TENANT_CREATE)));

        publish(EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "platform-token"),
            Map.of("permissions", List.of(TENANT_CREATE, TENANT_READ), "decision", "any"),
            Map.of("mode", "forbidden")));
        assertEquals("handled", invoke(platformToken(TENANT_READ)));
        assertDenied(403, EndpointAccessDeniedException.PERMISSION_DENIED, platformToken("platform:admin:read"));
    }

    @Test
    void namedAuthorizationPolicyRefIsNeverEnforcedAsPermission() {
        publish(EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "platform-token"),
            Map.of("permissions", List.of(TENANT_CREATE), "policyRef", TENANT_READ),
            Map.of("mode", "forbidden")));
        assertDenied(403, EndpointAccessDeniedException.PERMISSION_DENIED, platformToken(TENANT_READ));
        assertEquals("handled", invoke(platformToken(TENANT_CREATE)));

        publish(TestEndpointAccessPolicies.anonymous(TENANT_CREATE));
        assertEquals("handled", invoke(Map.of()));
    }

    @Test
    void authenticatedUserPolicyAcceptsOnlyTenantUserAccessTokens() {
        publish(EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "authenticated-user"),
            Map.of("permissions", List.of("app:read")),
            Map.of("mode", "optional")));

        assertEquals("handled", invoke(caller("2001", "42", "actor", "access", "actor", "app:read")));
        assertEquals("handled", invoke(caller("3001", "42", "subject", "access", "subject", "app:read")));
        assertDenied(403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
            caller("1001", null, "platform-admin", "access", "PLATFORM", "app:read"));
        assertDenied(403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
            caller("2001", null, "actor", "identity", "identity", "app:read"));
    }

    @Test
    void requiredTenantContextRejectsCallerWithoutTenant() {
        publish(EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "authenticated-user"),
            Map.of("permissions", List.of("tenant:read")),
            Map.of("mode", "required")));

        assertDenied(403, EndpointAccessDeniedException.TENANT_CONTEXT_REQUIRED,
            caller("2001", null, "actor", "access", "actor", "tenant:read"));
        assertEquals("handled", invoke(caller("2001", "42", "actor", "access", "actor", "tenant:read")));
    }

    @Test
    void anonymousEndpointRunsWithoutPrincipalButStillForbidsTenantContext() {
        publish(TestEndpointAccessPolicies.anonymous("platform-auth:login"));

        assertEquals("handled", invoke(Map.of()));
        assertDenied(403, EndpointAccessDeniedException.TENANT_CONTEXT_FORBIDDEN,
            caller("2001", "42", "actor", "access", "actor"));
    }

    private void publish(EndpointAccessPolicy policy) {
        dispatcher.replaceSnapshot(List.of(new EndpointRoute(
            "platform-admin",
            "platform.tenants.create.v1",
            "POST",
            "/api/platform/tenants",
            policy,
            handler)));
        handler.invocations.clear();
    }

    private Object invoke(Map<String, List<String>> headers) {
        return dispatcher.invoke("POST", "/api/platform/tenants", Map.of("code", "acme"), Map.of(), headers);
    }

    private void assertDenied(int status, String errorCode, Map<String, List<String>> headers) {
        int before = handler.invocations.size();
        EndpointAccessDeniedException denied = assertThrows(
            EndpointAccessDeniedException.class,
            () -> invoke(headers));
        assertEquals(status, denied.status());
        assertEquals(errorCode, denied.errorCode());
        assertEquals("platform.tenants.create.v1", denied.endpointId());
        assertEquals(before, handler.invocations.size(), "handler must not run for a denied invocation");
    }

    private static Map<String, List<String>> platformToken(String... permissions) {
        return caller("1001", null, "platform-admin", "access", "PLATFORM", permissions);
    }

    private static Map<String, List<String>> caller(
            String actorId,
            String tenantId,
            String tokenRole,
            String tokenType,
            String tokenScope,
            String... permissions) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        put(headers, "x-actor-id", actorId);
        put(headers, "x-tenant-id", tenantId);
        put(headers, "x-auth-token-role", tokenRole);
        put(headers, "x-auth-token-type", tokenType);
        put(headers, "x-auth-token-scope", tokenScope);
        if (permissions.length > 0) {
            headers.put("x-auth-permission", List.of(permissions));
        }
        return Map.copyOf(headers);
    }

    private static void put(Map<String, List<String>> headers, String name, String value) {
        if (value != null) {
            headers.put(name, List.of(value));
        }
    }

    private static final class RecordingHandler
            implements EndpointHandler<EndpointInvocation<Object>, Object> {

        private final List<EndpointInvocation<Object>> invocations = new ArrayList<>();

        @Override
        public Object handle(EndpointInvocation<Object> invocation) {
            invocations.add(invocation);
            return "handled";
        }
    }
}
