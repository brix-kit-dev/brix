package io.brix.platform.tenant.internal;

/** Command to accept a FIRST_OWNER invitation with an authenticated actor identity. */
public record AcceptFirstOwnerInvitationCommand(String invitationToken, Long identityId) {
    public AcceptFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requireText(invitationToken, "invitationToken");
        CreatePendingTenantCommand.requirePositive(identityId, "identityId");
    }
}
