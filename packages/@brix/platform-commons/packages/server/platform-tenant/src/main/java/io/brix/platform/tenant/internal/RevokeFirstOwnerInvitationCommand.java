package io.brix.platform.tenant.internal;

/** Command to revoke a pending FIRST_OWNER invitation. */
public record RevokeFirstOwnerInvitationCommand(Long tenantId, Long invitationId, String platformOperatorRef) {
    public RevokeFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
        CreatePendingTenantCommand.requirePositive(invitationId, "invitationId");
        CreatePendingTenantCommand.requireText(platformOperatorRef, "platformOperatorRef");
    }
}
