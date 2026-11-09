package io.brix.platform.notification;

import java.time.Duration;

/**
 * What the notification service reports about deliveries (notification decision D107, standard-link point 14a).
 * Labels are the template and the outcome only, never the recipient, a link, a variable or the content
 * (super-admin BRX-SA-NOTIFY-001).
 *
 * @author Brix Platform Team
 * @since 3.3.0
 */
public interface NotificationMetrics {

    /** The outcome of a message the delivery adapter got accepted. */
    String SENT = "sent";

    /** Records nothing. */
    NotificationMetrics NOOP = new NotificationMetrics() {
    };

    /**
     * A delivery ended.
     *
     * @param templateKey the template of the message
     * @param outcome {@value #SENT}, or why it was not: {@code rejected}, {@code timeout} or {@code failed}
     * @param took how long the delivery took
     */
    default void delivered(String templateKey, String outcome, Duration took) {
    }
}
