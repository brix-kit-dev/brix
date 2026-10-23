package io.brix.platform.tenant.endpoint;

import java.util.Map;
import java.util.TreeMap;

import io.runtime.sdk.plugin.AccessScope;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Describes the calling staff member's current tenant context: tenant, member and the
 * effective permissions the Runtime grant table gives its verified role (multi-tenant 5.1
 * {@code BRX-TENANT-AUTHZ-009}). The BFF session endpoint returns this set to the tenant
 * console; the handler only relays what the Dispatcher computed and never derives
 * permissions itself.
 */
public final class TenantSessionHandler implements EndpointHandler<EndpointInvocation<Object>, TenantSessionDto> {

    @Override
    public TenantSessionDto handle(EndpointInvocation<Object> invocation) {
        String tenantId = invocation.tenantId().orElseThrow(() -> PlatformTenantEndpointErrors.forbidden(
                "TENANT_CONTEXT_REQUIRED", "a tenant context is required"));
        String memberType = invocation.access().tenantRoleType().orElseThrow(() -> PlatformTenantEndpointErrors.forbidden(
                "TENANT_ACTOR_REQUIRED", "a verified tenant member is required"));
        Map<String, String> scopes = new TreeMap<>();
        for (Map.Entry<String, AccessScope> entry : invocation.access().effectivePermissions().entrySet()) {
            scopes.put(entry.getKey(), entry.getValue().name());
        }
        return new TenantSessionDto(tenantId, invocation.access().tenantRefId().orElse(null), memberType,
                scopes.keySet().stream().toList(), scopes);
    }
}
