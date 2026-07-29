package io.brix.platform.tenant.internal;

/** Command to revoke and recreate a FIRST_OWNER invitation. */
public record ResendFirstOwnerInvitationCommand(
        Long tenantId,
        String platformOperatorRef,
        String inviteBaseUrl,
        String locale) {

    public ResendFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
        CreatePendingTenantCommand.requireText(platformOperatorRef, "platformOperatorRef");
        CreatePendingTenantCommand.requireText(inviteBaseUrl, "inviteBaseUrl");
    }
}
