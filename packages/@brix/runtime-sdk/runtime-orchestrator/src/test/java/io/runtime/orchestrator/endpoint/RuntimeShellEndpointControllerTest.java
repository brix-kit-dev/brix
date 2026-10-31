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
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import io.runtime.sdk.capability.AuthenticatedPrincipal;
import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.DataScope;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.error.ErrorCode;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.query.BrixQueryException;

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
    void dispatchNeverPassesBrowserCookiesToHandlers() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> new TestAuthCapability("1001", null));

        controller.dispatch(null, request(Map.of(
            "Cookie", List.of("__Host-brix-session=opaque-session-id"),
            "Accept", List.of("application/json"))));

        assertFalse(dispatcher.headers.containsKey("cookie"));
        assertEquals(List.of("application/json"), dispatcher.headers.get("accept"));
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

    @Test
    void dispatchForwardsOnlyVerifiedTokenScopeAndPermissions() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> platformAdmin(Set.of("platform:tenant:read")));

        controller.dispatch(null, request(Map.of(
            "X-Auth-Token-Scope", List.of("spoofed"),
            "X-Auth-Permission", List.of("platform:tenant:create"))));

        assertEquals(List.of("PLATFORM"), dispatcher.headers.get("x-auth-token-scope"));
        assertEquals(List.of("platform:tenant:read"), dispatcher.headers.get("x-auth-permission"));
    }

    @Test
    void dispatchForwardsOnlyVerifiedTenantRoleTypeAndReference() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> new TenantAuthCapability("2001", "42", "actor", "MEMBER", "77"));

        controller.dispatch(null, request(Map.of(
            "X-Auth-Tenant-Role-Type", List.of("OWNER"),
            "X-Auth-Tenant-Ref-Id", List.of("1"))));

        assertEquals(List.of("MEMBER"), dispatcher.headers.get("x-auth-tenant-role-type"));
        assertEquals(List.of("77"), dispatcher.headers.get("x-auth-tenant-ref-id"));
    }

    @Test
    void dispatchDropsClientTenantRoleHeadersWhenNoVerifiedPrincipalExists() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> new TestAuthCapability(null, null));

        controller.dispatch(null, request(Map.of(
            "X-Auth-Tenant-Role-Type", List.of("OWNER"),
            "X-Auth-Tenant-Ref-Id", List.of("1"))));

        assertFalse(dispatcher.headers.containsKey("x-auth-tenant-role-type"));
        assertFalse(dispatcher.headers.containsKey("x-auth-tenant-ref-id"));
    }

    @Test
    void dispatchDropsClientPermissionHeadersWhenNoVerifiedPrincipalExists() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        RuntimeShellEndpointController controller = new RuntimeShellEndpointController(
            dispatcher,
            () -> new TestAuthCapability(null, null));

        controller.dispatch(null, request(Map.of(
            "X-Auth-Token-Role", List.of("platform-admin"),
            "X-Auth-Token-Type", List.of("access"),
            "X-Auth-Token-Scope", List.of("PLATFORM"),
            "X-Auth-Permission", List.of("platform:tenant:create"))));

        assertFalse(dispatcher.headers.containsKey("x-auth-token-role"));
        assertFalse(dispatcher.headers.containsKey("x-auth-token-type"));
        assertFalse(dispatcher.headers.containsKey("x-auth-token-scope"));
        assertFalse(dispatcher.headers.containsKey("x-auth-permission"));
    }

    @Test
    void dispatcherDenialBecomesProtocolErrorBeforeHandlerRuns() {
        AtomicInteger handled = new AtomicInteger();
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        dispatcher.replaceSnapshot(List.of(new EndpointRoute(
            "platform-admin",
            "platform.admins.list.v1",
            "GET",
            "/api/platform/admins",
            TestEndpointAccessPolicies.platform("platform:admin:read"),
            (EndpointHandler<EndpointInvocation<Object>, Object>) invocation -> {
                handled.incrementAndGet();
                return Map.of("ok", true);
            })));

        ResponseEntity<Object> anonymous = new RuntimeShellEndpointController(
            dispatcher, () -> new TestAuthCapability(null, null))
            .dispatch(null, request(Map.of()));
        ResponseEntity<Object> actor = new RuntimeShellEndpointController(
            dispatcher, () -> new TestAuthCapability(
                "2001", "42", null, "actor", "access", Set.of(), "actor", Set.of("platform:admin:read")))
            .dispatch(null, request(Map.of("X-Auth-Permission", List.of("platform:admin:read"))));
        ResponseEntity<Object> unprivileged = new RuntimeShellEndpointController(
            dispatcher, () -> platformAdmin(Set.of("platform:tenant:read")))
            .dispatch(null, request(Map.of()));

        assertEquals(401, anonymous.getStatusCode().value());
        assertEquals(Map.of(
                "errorCode", EndpointAccessDeniedException.AUTHENTICATION_REQUIRED,
                "message", "Authentication is required"),
            anonymous.getBody());
        assertEquals(403, actor.getStatusCode().value());
        assertEquals(EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED, errorCode(actor));
        assertEquals(403, unprivileged.getStatusCode().value());
        assertEquals(EndpointAccessDeniedException.PERMISSION_DENIED, errorCode(unprivileged));
        assertEquals(0, handled.get());

        ResponseEntity<Object> granted = new RuntimeShellEndpointController(
            dispatcher, () -> platformAdmin(Set.of("platform:admin:read")))
            .dispatch(null, request(Map.of()));

        assertEquals(200, granted.getStatusCode().value());
        assertEquals(1, handled.get());
    }

    @Test
    void aQueryFailureTheHandlerDidNotHandleBecomesItsStableCodeAndAStatus() {
        // Query design §5.4: unavailable 503, late 504, denied 403, failing provider platform.unknown; never the cause.
        Map<BrixQueryException.Code, List<Object>> expected = Map.of(
            BrixQueryException.Code.PROVIDER_MISSING, List.of(503, "query.provider_missing"),
            BrixQueryException.Code.CIRCUIT_OPEN, List.of(503, "query.circuit_open"),
            BrixQueryException.Code.REJECTED, List.of(503, "query.rejected"),
            BrixQueryException.Code.DEADLINE_EXCEEDED, List.of(504, "query.deadline_exceeded"),
            BrixQueryException.Code.ACCESS_DENIED, List.of(403, "query.access_denied"),
            BrixQueryException.Code.PROVIDER_FAILED, List.of(500, "platform.unknown"));
        expected.forEach((code, response) -> {
            DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
            dispatcher.replaceSnapshot(List.of(new EndpointRoute(
                "platform-admin",
                "platform.admins.list.v1",
                "GET",
                "/api/platform/admins",
                TestEndpointAccessPolicies.platform("platform:admin:read"),
                (EndpointHandler<EndpointInvocation<Object>, Object>) invocation -> {
                    throw new BrixQueryException(code, Map.of("query", "platform-tenant.tenant-reference"),
                        new IllegalStateException("connection refused by db-7.internal"));
                })));

            ResponseEntity<Object> failed = new RuntimeShellEndpointController(
                dispatcher, () -> platformAdmin(Set.of("platform:admin:read")))
                .dispatch(null, request(Map.of()));

            assertEquals(response.get(0), failed.getStatusCode().value(), code.name());
            assertEquals(Map.of("errorCode", response.get(1), "message", response.get(1)), failed.getBody());
        });
    }

    @Test
    void anExpectedFailureAnswersWithItsCodeAtTheStatusOfItsCategory() {
        // Root BRX-RS-MANIFEST-022; standard-link plan L7.3: the handler names the code, the runtime the status.
        Map<ErrorCategory, Integer> expected = Map.of(ErrorCategory.INVALID, 400, ErrorCategory.DENIED, 403,
            ErrorCategory.NOT_FOUND, 404, ErrorCategory.CONFLICT, 409, ErrorCategory.UNAVAILABLE, 503,
            ErrorCategory.DEADLINE_EXCEEDED, 504, ErrorCategory.INTERNAL, 500);
        expected.forEach((category, status) -> {
            ResponseEntity<Object> failed = dispatchThrowing(new SampleFailure(category));

            assertEquals(status, failed.getStatusCode().value(), category.name());
            assertEquals(Map.of("errorCode", "sample.failure", "message", "sample.failure"), failed.getBody());
        });
    }

    @Test
    void anInvalidRequestAnswers400WithThePathsAndRulesItBreaks() {
        ResponseEntity<Object> failed = dispatchThrowing(new EndpointRequestInvalidException(
            List.of(Map.of("path", "$.size", "rule", "maximum"))));

        assertEquals(400, failed.getStatusCode().value());
        assertEquals(Map.of("errorCode", "endpoint.invalid_request", "message", "endpoint.invalid_request",
            "violations", List.of(Map.of("path", "$.size", "rule", "maximum"))), failed.getBody());
    }

    @Test
    void anUnexpectedFailureIsPlatformUnknownWithoutItsCause() {
        ResponseEntity<Object> failed = dispatchThrowing(new IllegalStateException("connection refused by db-7.internal"));

        assertEquals(500, failed.getStatusCode().value());
        assertEquals(Map.of("errorCode", "platform.unknown", "message", "platform.unknown"), failed.getBody());
        assertEquals(504, dispatchThrowing(new EndpointDeadlineExceededException("e")).getStatusCode().value());
        assertEquals(Map.of("errorCode", "platform.unknown", "message", "platform.unknown"),
            dispatchThrowing(new EndpointResponseInvalidException("e")).getBody());
    }

    private static ResponseEntity<Object> dispatchThrowing(RuntimeException failure) {
        PluginEndpointDispatcher dispatcher = new PluginEndpointDispatcher() {
            @Override
            public void replaceSnapshot(Collection<EndpointRoute> routes) {
            }

            @Override
            public Object invoke(String method, String path, Object body, Map<String, List<String>> queryParameters,
                    Map<String, List<String>> headers) {
                throw failure;
            }

            @Override
            public List<EndpointRoute> routes() {
                return List.of();
            }
        };
        return new RuntimeShellEndpointController(dispatcher, () -> platformAdmin(Set.of()))
            .dispatch(null, request(Map.of()));
    }

    private static final class SampleFailure extends BrixException {

        private static final long serialVersionUID = 1L;

        SampleFailure(ErrorCategory category) {
            super(new ErrorCode() {
                @Override
                public String wireCode() {
                    return "sample.failure";
                }

                @Override
                public ErrorCategory category() {
                    return category;
                }
            }, Map.of(), null);
        }
    }

    private static TestAuthCapability platformAdmin(Set<String> permissions) {
        return new TestAuthCapability(
            "1001", null, "admin@example.test", "platform-admin", "access", Set.of(), "PLATFORM", permissions);
    }

    private static Object errorCode(ResponseEntity<Object> response) {
        return ((Map<?, ?>) response.getBody()).get("errorCode");
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
            Set<String> allowedActions,
            String tokenScope,
            Set<String> permissions) implements AuthCapability {

        private TestAuthCapability(String actorId, String tenantId) {
            this(actorId, tenantId, null, null, null, Set.of());
        }

        private TestAuthCapability(
                String actorId,
                String tenantId,
                String email,
                String tokenRole,
                String tokenType,
                Set<String> allowedActions) {
            this(actorId, tenantId, email, tokenRole, tokenType, allowedActions, null, Set.of());
        }

        @Override
        public Principal getCurrentPrincipal() {
            return actorId == null
                ? null
                : new TestPrincipal(
                    actorId, tenantId, email, tokenRole, tokenType, allowedActions, tokenScope, permissions);
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

    private record TenantAuthCapability(
            String actorId,
            String tenantId,
            String tokenRole,
            String tenantRoleType,
            String tenantRefId) implements AuthCapability {

        @Override
        public Principal getCurrentPrincipal() {
            return new TenantPrincipal(actorId, tenantId, tokenRole, tenantRoleType, tenantRefId);
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

    private record TenantPrincipal(
            String userId,
            String tenantId,
            String tokenRole,
            String tenantRoleType,
            String tenantRefId) implements AuthenticatedPrincipal {

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
            return null;
        }

        @Override
        public String getTokenRole() {
            return tokenRole;
        }

        @Override
        public String getTokenType() {
            return "access";
        }

        @Override
        public String getTokenScope() {
            return tokenRole;
        }

        @Override
        public String getTenantRoleType() {
            return tenantRoleType;
        }

        @Override
        public String getTenantRefId() {
            return tenantRefId;
        }
    }

    private record TestPrincipal(
            String userId,
            String tenantId,
            String email,
            String tokenRole,
            String tokenType,
            Set<String> allowedActions,
            String tokenScope,
            Set<String> permissions) implements AuthenticatedPrincipal {

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

        @Override
        public String getTokenScope() {
            return tokenScope;
        }

        @Override
        public Set<String> getPermissions() {
            return permissions;
        }
    }
}
