package io.brix.platform.tenant.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.AccessScope;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

class TenantSessionHandlerTest {

    private final TenantSessionHandler handler = new TenantSessionHandler();

    @Test
    void relaysTheEffectivePermissionsTheDispatcherComputedForTheCallersRole() {
        TenantSessionDto session = handler.handle(invocation(Optional.of("42"), new AccessDecision(
                Set.of("tenant:profile:read"),
                Map.of("tenant:profile:read", AccessScope.TENANT),
                Optional.of("MEMBER"),
                Optional.of("7"),
                Map.of("tenant:profile:read", AccessScope.TENANT,
                        "tenant:member:read", AccessScope.SELF,
                        "app-booking:template:read", AccessScope.TENANT))));

        assertEquals("42", session.tenantId());
        assertEquals("7", session.memberId());
        assertEquals("MEMBER", session.memberType());
        assertEquals(List.of("app-booking:template:read", "tenant:member:read", "tenant:profile:read"),
                session.permissions());
        assertEquals("SELF", session.scopes().get("tenant:member:read"));
    }

    @Test
    void requiresAVerifiedTenantMember() {
        assertThrows(EndpointHandlingException.class,
                () -> handler.handle(invocation(Optional.of("42"), AccessDecision.none())));
        assertThrows(EndpointHandlingException.class, () -> handler.handle(invocation(Optional.empty(),
                new AccessDecision(Set.of(), Map.of(), Optional.of("MEMBER"), Optional.of("7")))));
    }

    private static EndpointInvocation<Object> invocation(Optional<String> tenantId, AccessDecision access) {
        return new EndpointInvocation<>(null, Map.of(), Map.of(), Map.of(), tenantId, Optional.of("21"),
                Optional.empty(), Optional.of("actor"), Optional.of("access"), Set.of(), Optional.empty(),
                Instant.now().plusSeconds(30), access);
    }
}
