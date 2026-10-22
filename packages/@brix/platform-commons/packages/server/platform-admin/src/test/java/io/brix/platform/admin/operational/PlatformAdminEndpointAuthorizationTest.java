package io.brix.platform.admin.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.RoleCode;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.EndpointAccessDeniedException;
import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.operational.OperationalModuleDescriptorLoader;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Runs the shipped {@code platform-operational.yaml} through the L2B Dispatcher
 * and proves that every unauthorized call to {@code /api/platform/**} is
 * rejected before its handler.
 */
class PlatformAdminEndpointAuthorizationTest {

    private static final Map<String, String> CATALOG_PERMISSIONS = Map.of(
        "platform.admins.list.v1", "platform:admin:read",
        "platform.tenants.list.v1", "platform:tenant:read",
        "platform.license.quota.v1", "platform:license:read",
        "platform.tenants.create.v1", "platform:tenant:create",
        "platform.tenants.first-owner-invitations.create.v1", "platform:tenant:first-owner-invite",
        "platform.tenants.first-owner-invitations.current.v1", "platform:tenant:first-owner-invite",
        "platform.tenants.first-owner-invitations.resend.v1", "platform:tenant:first-owner-invite",
        "platform.tenants.first-owner-invitations.revoke.v1", "platform:tenant:first-owner-invite");

    private static final List<String> SUPER_ADMIN_PERMISSIONS =
        PlatformPermissions.defaultPermissionsFor(RoleCode.PLATFORM_SUPER_ADMIN);

    private final OperationalModuleDescriptor descriptor = descriptor();
    private final Map<String, AtomicInteger> handled = new LinkedHashMap<>();
    private final DefaultPluginEndpointDispatcher dispatcher = dispatcher();

    @Test
    void everyAuthenticatedEndpointRequiresPlatformTokenAndItsCatalogPermission() {
        Map<String, String> declared = new LinkedHashMap<>();
        for (OperationalModuleDescriptor.EndpointDeclaration endpoint : descriptor.endpoints().values()) {
            EndpointAccessPolicy policy = endpoint.accessPolicy();
            assertEquals(EndpointAccessPolicy.TenantContextMode.FORBIDDEN, policy.tenantContext(),
                endpoint.endpointId());
            if (policy.requiresVerifiedPrincipal()) {
                assertEquals(EndpointAccessPolicy.TokenPolicy.PLATFORM_TOKEN, policy.tokenPolicy(),
                    endpoint.endpointId());
                assertEquals(1, policy.permissions().size(), endpoint.endpointId());
                declared.put(endpoint.endpointId(), policy.permissions().iterator().next());
            } else {
                assertTrue(policy.permissions().isEmpty(), endpoint.endpointId());
            }
        }

        assertEquals(CATALOG_PERMISSIONS, declared);
    }

    @Test
    void formalSuperAdministratorIsGrantedEveryDeclaredPermission() {
        assertTrue(SUPER_ADMIN_PERMISSIONS.containsAll(CATALOG_PERMISSIONS.values()));
        assertFalse(SUPER_ADMIN_PERMISSIONS.contains(PlatformPermissions.BYPASS_PERMISSION_CHECK));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authenticatedEndpointIds")
    void unauthorizedCallsAreRejectedBeforeTheHandler(String endpointId) {
        String permission = CATALOG_PERMISSIONS.get(endpointId);
        List<String> allPermissions = SUPER_ADMIN_PERMISSIONS;
        List<String> allButRequired = SUPER_ADMIN_PERMISSIONS.stream()
            .filter(granted -> !granted.equals(permission))
            .toList();

        assertDenied(endpointId, 401, EndpointAccessDeniedException.AUTHENTICATION_REQUIRED, Map.of());
        assertDenied(endpointId, 403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
            caller(null, "platform-admin", "mfa_challenge", "PLATFORM", allPermissions));
        assertDenied(endpointId, 403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
            caller(null, "bootstrap", "BOOTSTRAP_SETUP", "BOOTSTRAP", allPermissions));
        assertDenied(endpointId, 403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
            caller(null, "actor", "identity", "identity", List.of()));
        assertDenied(endpointId, 403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
            caller("42", "actor", "access", "actor", allPermissions));
        assertDenied(endpointId, 403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
            caller("42", "subject", "access", "subject", allPermissions));
        assertDenied(endpointId, 403, EndpointAccessDeniedException.TENANT_CONTEXT_FORBIDDEN,
            caller("42", "platform-admin", "access", "PLATFORM", allPermissions));
        assertDenied(endpointId, 403, EndpointAccessDeniedException.PERMISSION_DENIED,
            caller(null, "platform-admin", "access", "PLATFORM", allButRequired));
        assertDenied(endpointId, 403, EndpointAccessDeniedException.PERMISSION_DENIED,
            caller(null, "platform-admin", "access", "PLATFORM", List.of()));
        assertEquals(0, handled.get(endpointId).get());

        assertEquals("handled:" + endpointId,
            invoke(endpointId, caller(null, "platform-admin", "access", "PLATFORM", allPermissions)));
        assertEquals(1, handled.get(endpointId).get());
    }

    @Test
    void publicPlatformEntriesStayReachableWithoutPlatformToken() {
        Set<String> publicEndpoints = descriptor.endpoints().values().stream()
            .filter(endpoint -> !endpoint.accessPolicy().requiresVerifiedPrincipal())
            .map(OperationalModuleDescriptor.EndpointDeclaration::endpointId)
            .collect(Collectors.toCollection(HashSet::new));

        assertEquals(Set.of(
            "platform.bootstrap.status.v1",
            "platform.bootstrap.session.v1",
            "platform.bootstrap.create-first-admin.v1",
            "platform.auth.setup.validate.v1",
            "platform.auth.setup.totp.init.v1",
            "platform.auth.setup.complete.v1",
            "platform.auth.login.v1",
            "platform.auth.login.totp.v1"), publicEndpoints);
        for (String endpointId : publicEndpoints) {
            assertEquals("handled:" + endpointId, invoke(endpointId, Map.of()));
        }
    }

    static Stream<String> authenticatedEndpointIds() {
        return CATALOG_PERMISSIONS.keySet().stream().sorted();
    }

    private void assertDenied(String endpointId, int status, String errorCode, Map<String, List<String>> caller) {
        EndpointAccessDeniedException denied = assertThrows(
            EndpointAccessDeniedException.class,
            () -> invoke(endpointId, caller),
            endpointId + " " + caller);
        assertEquals(status, denied.status(), endpointId + " " + caller);
        assertEquals(errorCode, denied.errorCode(), endpointId + " " + caller);
    }

    private Object invoke(String endpointId, Map<String, List<String>> caller) {
        OperationalModuleDescriptor.EndpointDeclaration endpoint = descriptor.endpoints().get(endpointId);
        String path = endpoint.path().replaceAll("\\{[^/]+}", "7");
        return dispatcher.invoke(endpoint.method(), path, Map.of(), Map.of(), caller);
    }

    private DefaultPluginEndpointDispatcher dispatcher() {
        List<EndpointRoute> routes = new ArrayList<>();
        for (OperationalModuleDescriptor.EndpointDeclaration endpoint : descriptor.endpoints().values()) {
            AtomicInteger counter = new AtomicInteger();
            handled.put(endpoint.endpointId(), counter);
            routes.add(new EndpointRoute(
                descriptor.identity().moduleId(),
                endpoint.endpointId(),
                endpoint.method(),
                endpoint.path(),
                endpoint.accessPolicy(),
                (EndpointHandler<EndpointInvocation<Object>, Object>) invocation -> {
                    counter.incrementAndGet();
                    return "handled:" + endpoint.endpointId();
                }));
        }
        DefaultPluginEndpointDispatcher result = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        result.replaceSnapshot(routes);
        return result;
    }

    private static OperationalModuleDescriptor descriptor() {
        return new OperationalModuleDescriptorLoader().load(
            PlatformAdminEndpointAuthorizationTest.class.getClassLoader().getResource(
                OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE));
    }

    private static Map<String, List<String>> caller(
            String tenantId,
            String tokenRole,
            String tokenType,
            String tokenScope,
            List<String> permissions) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-actor-id", List.of("1001"));
        if (tenantId != null) {
            headers.put("x-tenant-id", List.of(tenantId));
        }
        headers.put("x-auth-token-role", List.of(tokenRole));
        headers.put("x-auth-token-type", List.of(tokenType));
        headers.put("x-auth-token-scope", List.of(tokenScope));
        if (!permissions.isEmpty()) {
            headers.put("x-auth-permission", List.copyOf(permissions));
        }
        return Map.copyOf(headers);
    }
}
