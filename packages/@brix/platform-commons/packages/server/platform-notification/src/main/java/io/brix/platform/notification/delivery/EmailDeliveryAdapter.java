package io.brix.platform.notification.delivery;

/**
 * Adapter port for one-shot plain-text email delivery.
 *
 * <p>Implementations belong to infrastructure adapter modules. They must not
 * choose business templates, inspect tokens, or participate in owner
 * transactions.</p>
 *
 * <p>A delivery is a synchronous boundary, so an implementation bounds it (root BRX-RS-RESILIENCE-001;
 * notification decision D104, standard-link point 14a): every step it waits on has a timeout, and the whole
 * delivery ends by the deadline of the call it runs in.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public interface EmailDeliveryAdapter {

    /**
     * Delivers the rendered email message once.
     *
     * @param message rendered email message
     * @throws EmailDeliveryException when the message was not accepted, with the reason
     */
    void deliver(EmailMessage message);
}
