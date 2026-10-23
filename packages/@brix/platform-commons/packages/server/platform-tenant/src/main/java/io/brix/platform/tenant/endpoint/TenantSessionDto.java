package io.brix.platform.tenant.endpoint;

import java.util.List;
import java.util.Map;

/**
 * The calling staff member's tenant context.
 *
 * @param tenantId    current tenant
 * @param memberId    verified member id
 * @param memberType  verified member type ({@code OWNER}, {@code ADMIN}, {@code MEMBER})
 * @param permissions effective permissions in this context, sorted
 * @param scopes      data scope ({@code TENANT} or {@code SELF}) of each permission
 */
public record TenantSessionDto(
        String tenantId,
        String memberId,
        String memberType,
        List<String> permissions,
        Map<String, String> scopes
) {
    public TenantSessionDto {
        permissions = List.copyOf(permissions);
        scopes = Map.copyOf(scopes);
    }
}
