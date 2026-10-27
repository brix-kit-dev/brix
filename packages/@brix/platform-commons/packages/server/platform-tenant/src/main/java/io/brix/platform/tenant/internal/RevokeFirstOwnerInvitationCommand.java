package io.brix.platform.tenant.internal;

import io.runtime.sdk.capability.TenantTarget;

/** Command to revoke a pending FIRST_OWNER invitation. */
public record RevokeFirstOwnerInvitationCommand(Long tenantId, Long invitationId, String platformOperatorRef)
        implements TenantTarget {
    public RevokeFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
        CreatePendingTenantCommand.requirePositive(invitationId, "invitationId");
        CreatePendingTenantCommand.requireText(platformOperatorRef, "platformOperatorRef");
    }

    @Override
    public Long targetTenantId() {
        return tenantId;
    }
}
