/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.RuntimeShellEndpointController;
import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.DataScope;

class RuntimeShellBootstrapAutoConfigurationTest {

    @Test
    void webConfigurationWiresAuthCapabilityProviderIntoEndpointController() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller =
                new RuntimeShellBootstrapAutoConfiguration.RuntimeShellWebConfiguration()
                        .runtimeShellEndpointController(dispatcher, provider(new TestAuthCapability("1001")));

        controller.dispatch(null, request());

        assertEquals(List.of("1001"), dispatcher.headers.get("x-actor-id"));
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/platform/admins");
        when(request.getContextPath()).thenReturn("");
        when(request.getParameterMap()).thenReturn(Map.of());
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        return request;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuthCapability> provider(AuthCapability authCapability) {
        ObjectProvider<AuthCapability> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(authCapability);
        return provider;
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
            return Map.of(
                    "ok", true,
                    "at", Instant.EPOCH.toString());
        }

        @Override
        public List<EndpointRoute> routes() {
            return List.of();
        }
    }

    private record TestAuthCapability(String actorId) implements AuthCapability {

        @Override
        public Principal getCurrentPrincipal() {
            return () -> actorId;
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
    }
}
