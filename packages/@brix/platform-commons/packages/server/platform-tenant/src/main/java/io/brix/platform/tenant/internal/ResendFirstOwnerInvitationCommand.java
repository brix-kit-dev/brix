package io.brix.platform.tenant.internal;

import io.runtime.sdk.capability.TenantTarget;

/** Command to revoke and recreate a FIRST_OWNER invitation. */
public record ResendFirstOwnerInvitationCommand(
        Long tenantId,
        String platformOperatorRef,
        String locale) implements TenantTarget {

    public ResendFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
        CreatePendingTenantCommand.requireText(platformOperatorRef, "platformOperatorRef");
    }

    @Override
    public Long targetTenantId() {
        return tenantId;
    }
}
