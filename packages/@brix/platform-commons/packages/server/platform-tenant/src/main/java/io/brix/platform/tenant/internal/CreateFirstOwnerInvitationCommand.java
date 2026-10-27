package io.brix.platform.tenant.internal;

import io.runtime.sdk.capability.TenantTarget;

/** Command to create a FIRST_OWNER invitation. */
public record CreateFirstOwnerInvitationCommand(
        Long tenantId,
        String inviteeEmail,
        String platformOperatorRef,
        String locale) implements TenantTarget {

    public CreateFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
        CreatePendingTenantCommand.requireText(inviteeEmail, "inviteeEmail");
        CreatePendingTenantCommand.requireText(platformOperatorRef, "platformOperatorRef");
    }

    @Override
    public Long targetTenantId() {
        return tenantId;
    }
}
