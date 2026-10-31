package io.brix.platform.tenant.endpoint;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.runtime.sdk.plugin.AccessScope;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * Describes the calling staff member's current tenant context: tenant, member and the
 * effective permissions the Runtime grant table gives its verified role (multi-tenant 5.1
 * {@code BRX-TENANT-AUTHZ-009}). The BFF session endpoint returns this set to the tenant
 * console; the handler only relays what the Dispatcher computed and never derives
 * permissions itself.
 *
 * <p>The request takes no parameters and the answer is checked against
 * {@code brix://schemas/platform-tenant/tenant-session/1.0.0} (standard-link plan L7.9).</p>
 */
public final class TenantSessionHandler implements TypedEndpointHandler<Object, TenantSessionDto> {

    @Override
    public CompletionStage<TenantSessionDto> handle(EndpointInvocation<Object> invocation) {
        String tenantId = invocation.tenantId().orElseThrow(() -> PlatformTenantEndpointErrors.forbidden(
                "TENANT_CONTEXT_REQUIRED", "a tenant context is required"));
        String memberType = invocation.access().tenantRoleType().orElseThrow(() -> PlatformTenantEndpointErrors.forbidden(
                "TENANT_ACTOR_REQUIRED", "a verified tenant member is required"));
        Map<String, String> scopes = new TreeMap<>();
        for (Map.Entry<String, AccessScope> entry : invocation.access().effectivePermissions().entrySet()) {
            scopes.put(entry.getKey(), entry.getValue().name());
        }
        return CompletableFuture.completedFuture(new TenantSessionDto(tenantId,
                invocation.access().tenantRefId().orElse(null), memberType, scopes.keySet().stream().toList(), scopes));
    }
}
