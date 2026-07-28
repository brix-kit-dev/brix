package io.brix.platform.notification.delivery;

/**
 * Adapter port for one-shot plain-text email delivery.
 *
 * <p>Implementations belong to infrastructure adapter modules. They must not
 * choose business templates, inspect tokens, or participate in owner
 * transactions.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public interface EmailDeliveryAdapter {

    /**
     * Delivers the rendered email message once.
     *
     * @param message rendered email message
     */
    void deliver(EmailMessage message);
}
