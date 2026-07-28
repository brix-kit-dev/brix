package io.brix.platform.tenant.internal;

/** Command to revoke and recreate a FIRST_OWNER invitation. */
public record ResendFirstOwnerInvitationCommand(
        Long tenantId,
        Long platformAdminIdentityId,
        String inviteBaseUrl,
        String locale) {

    public ResendFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
        CreatePendingTenantCommand.requirePositive(platformAdminIdentityId, "platformAdminIdentityId");
        CreatePendingTenantCommand.requireText(inviteBaseUrl, "inviteBaseUrl");
    }
}
