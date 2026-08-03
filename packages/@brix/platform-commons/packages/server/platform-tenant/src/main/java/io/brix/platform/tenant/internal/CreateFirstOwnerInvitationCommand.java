package io.brix.platform.tenant.internal;

/** Command to create a FIRST_OWNER invitation. */
public record CreateFirstOwnerInvitationCommand(
        Long tenantId,
        String inviteeEmail,
        String platformOperatorRef,
        String locale) {

    public CreateFirstOwnerInvitationCommand {
        CreatePendingTenantCommand.requirePositive(tenantId, "tenantId");
        CreatePendingTenantCommand.requireText(inviteeEmail, "inviteeEmail");
        CreatePendingTenantCommand.requireText(platformOperatorRef, "platformOperatorRef");
    }
}
