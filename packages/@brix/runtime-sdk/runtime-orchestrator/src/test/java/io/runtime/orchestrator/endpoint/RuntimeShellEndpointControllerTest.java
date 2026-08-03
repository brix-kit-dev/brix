/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.AuthenticatedPrincipal;
import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.DataScope;

class RuntimeShellEndpointControllerTest {

    @Test
    void dispatchUsesVerifiedAuthCapabilityActorInsteadOfClientActorHeader() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> new TestAuthCapability("1001", null));

        controller.dispatch(null, request(Map.of(
            "X-Actor-Id", List.of("spoofed"),
            "X-Tenant-Id", List.of("tenant-spoof"),
            "Authorization", List.of("Bearer signed-token"))));

        assertEquals(List.of("1001"), dispatcher.headers.get("x-actor-id"));
        assertFalse(dispatcher.headers.containsKey("x-tenant-id"));
        assertEquals(List.of("Bearer signed-token"), dispatcher.headers.get("authorization"));
    }

    @Test
    void dispatchRemovesClientActorHeaderWhenNoVerifiedPrincipalExists() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> new TestAuthCapability(null, null));

        controller.dispatch(null, request(Map.of(
            "X-Actor-Id", List.of("spoofed"),
            "X-Tenant-Id", List.of("tenant-spoof"))));

        assertFalse(dispatcher.headers.containsKey("x-actor-id"));
        assertFalse(dispatcher.headers.containsKey("x-tenant-id"));
    }

    @Test
    void dispatchInjectsOnlyVerifiedExtendedAuthContext() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> new TestAuthCapability(
                "1001",
                null,
                "owner@example.test",
                "actor",
                "identity",
                Set.of("first_owner_accept")));

        controller.dispatch(null, request(Map.of(
            "X-Auth-Identity-Email", List.of("spoof@example.test"),
            "X-Auth-Token-Role", List.of("platform-admin"),
            "X-Auth-Token-Type", List.of("access"),
            "X-Auth-Allowed-Action", List.of("spoof"))));

        assertEquals(List.of("owner@example.test"), dispatcher.headers.get("x-auth-identity-email"));
        assertEquals(List.of("actor"), dispatcher.headers.get("x-auth-token-role"));
        assertEquals(List.of("identity"), dispatcher.headers.get("x-auth-token-type"));
        assertEquals(List.of("first_owner_accept"), dispatcher.headers.get("x-auth-allowed-action"));
    }

    private static HttpServletRequest request(Map<String, List<String>> headers) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/platform/admins");
        when(request.getContextPath()).thenReturn("");
        when(request.getParameterMap()).thenReturn(Map.of("page", new String[] {"0"}));
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(headers.keySet()));
        headers.forEach((name, values) ->
            when(request.getHeaders(name)).thenReturn(Collections.enumeration(values)));
        return request;
    }

    private static final class CapturingDispatcher implements PluginEndpointDispatcher {

        private Map<String, List<String>> headers = Map.of();

        @Override
        public void replaceSnapshot(Collection<EndpointRoute> routes) {
        }

        @Override
        public Object invoke(
                String method,
                String path,
                Object body,
                Map<String, List<String>> queryParameters,
                Map<String, List<String>> headers) {
            this.headers = headers;
            return Map.of("ok", true);
        }

        @Override
        public List<EndpointRoute> routes() {
            return List.of();
        }
    }

    private record TestAuthCapability(
            String actorId,
            String tenantId,
            String email,
            String tokenRole,
            String tokenType,
            Set<String> allowedActions) implements AuthCapability {

        private TestAuthCapability(String actorId, String tenantId) {
            this(actorId, tenantId, null, null, null, Set.of());
        }

        @Override
        public Principal getCurrentPrincipal() {
            return actorId == null
                ? null
                : new TestPrincipal(actorId, tenantId, email, tokenRole, tokenType, allowedActions);
        }

        @Override
        public boolean hasPermission(String permission) {
            return false;
        }

        @Override
        public boolean hasRole(String role) {
            return false;
        }

        @Override
        public Set<DataScope> getAuthorizedScopes() {
            return Set.of();
        }

        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    private record TestPrincipal(
            String userId,
            String tenantId,
            String email,
            String tokenRole,
            String tokenType,
            Set<String> allowedActions) implements AuthenticatedPrincipal {

        @Override
        public String getName() {
            return userId;
        }

        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public String getTenantId() {
            return tenantId;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getTokenRole() {
            return tokenRole;
        }

        @Override
        public String getTokenType() {
            return tokenType;
        }

        @Override
        public Set<String> getAllowedActions() {
            return allowedActions;
        }
    }
}
