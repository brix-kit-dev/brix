package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * Capability contract for platform security notifications.
 *
 * <p>The platform-admin setup flow uses this contract to deliver setup links
 * without exposing setup tokens in REST responses. Implementations may use
 * SMTP, an enterprise notification service, or another host-provided channel.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Since("3.2.0")
public interface NotificationCapability {

    /**
     * Sends a setup or password-reset link to the target email address.
     *
     * @param email recipient email address
     * @param setupUrl full setup URL containing the one-time setup token
     * @param purpose stable purpose code, for example INITIAL_SETUP or PASSWORD_RESET
     */
    void sendSetupLink(String email, String setupUrl, String purpose);

    /**
     * Sends a managed notification request.
     *
     * <p>The default implementation preserves binary compatibility for legacy
     * providers that only implement {@link #sendSetupLink(String, String, String)}.
     * It bridges the platform setup and password-reset templates to the legacy
     * method. Providers that support the reusable notification contract should
     * override this method.</p>
     *
     * @param request immutable notification request
     */
    default void send(NotificationRequest request) {
        if (NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL.equals(request.templateKey())) {
            sendSetupLink(request.recipientEmail(), requireVariable(request, "setupUrl"), "INITIAL_SETUP");
            return;
        }
        if (NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_PASSWORD_RESET.equals(request.templateKey())) {
            sendSetupLink(request.recipientEmail(), requireVariable(request, "setupUrl"), "PASSWORD_RESET");
            return;
        }
        throw new NotificationException(
                NotificationException.Code.TEMPLATE_NOT_FOUND,
                java.util.Map.of("templateKey", request.templateKey()));
    }

    private static String requireVariable(NotificationRequest request, String variableName) {
        String value = request.variables().get(variableName);
        if (value == null) {
            throw new NotificationException(
                    NotificationException.Code.REQUEST_INVALID,
                    java.util.Map.of("field", "variables." + variableName));
        }
        return value;
    }
}
