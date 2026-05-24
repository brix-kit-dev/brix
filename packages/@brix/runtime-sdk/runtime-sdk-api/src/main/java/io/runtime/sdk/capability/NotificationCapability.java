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
}