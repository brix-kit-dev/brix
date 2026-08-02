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

    private record TestAuthCapability(String actorId, String tenantId) implements AuthCapability {

        @Override
        public Principal getCurrentPrincipal() {
            return actorId == null ? null : () -> actorId;
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
}
