package io.brix.platform.tenant.internal;

import io.runtime.sdk.capability.TenantTarget;

/** Query for the latest FIRST_OWNER invitation of one tenant. */
public record FirstOwnerInvitationLookup(Long tenantId) implements TenantTarget {
    public FirstOwnerInvitationLookup {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
    }

    @Override
    public Long targetTenantId() {
        return tenantId;
    }
}
