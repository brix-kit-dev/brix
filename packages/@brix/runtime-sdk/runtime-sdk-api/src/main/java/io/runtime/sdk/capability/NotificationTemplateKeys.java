package io.runtime.sdk.capability;

/**
 * Stable template keys for managed notification messages.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class NotificationTemplateKeys {

    /** Initial setup link for the first or newly created platform administrator. */
    public static final String PLATFORM_ADMIN_SETUP_INITIAL = "platform.admin.setup.initial";

    /** Password reset setup link for an existing platform administrator. */
    public static final String PLATFORM_ADMIN_SETUP_PASSWORD_RESET = "platform.admin.setup.password-reset";

    /** Initial invitation link for the first tenant owner. */
    public static final String TENANT_OWNER_INVITATION_INITIAL = "tenant.owner.invitation.initial";

    /** Initial setup link for a pending first tenant owner identity. */
    public static final String TENANT_OWNER_SETUP_INITIAL = "tenant.owner.setup.initial";

    private NotificationTemplateKeys() {
    }
}
