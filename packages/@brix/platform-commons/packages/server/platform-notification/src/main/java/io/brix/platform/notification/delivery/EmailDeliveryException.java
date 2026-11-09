package io.brix.platform.notification.delivery;

import java.util.Locale;
import java.util.Objects;

/**
 * Why an email delivery adapter did not get a message accepted (notification decision D107, standard-link
 * point 14a).
 *
 * <p>The message names the reason and a safe detail only, never the recipient, the content or the server's
 * reply text, because a reply can quote the full address. An adapter keeps a cause only when it carries none of
 * those.</p>
 *
 * @author Brix Platform Team
 * @since 3.3.0
 */
public final class EmailDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why the message was not accepted. */
    public enum Reason {
        /** The server answered with a refusal: of an address, of the message or of the login. */
        REJECTED,
        /** A connect, read or write ran past its timeout, or the time the delivery had ran out. */
        TIMEOUT,
        /** Anything else: the server could not be reached, TLS failed, the connection broke. */
        FAILED;

        /**
         * Returns the reason as metrics and logs name it.
         *
         * @return {@code rejected}, {@code timeout} or {@code failed}
         */
        public String tag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Reason reason;

    /**
     * Creates the failure.
     *
     * @param reason why the message was not accepted
     * @param detail a detail safe to log, such as the server's reply code or the failing step
     * @param cause the adapter's own failure, or {@code null} when it could carry the recipient or the content
     */
    public EmailDeliveryException(Reason reason, String detail, Throwable cause) {
        super("email delivery " + Objects.requireNonNull(reason, "reason must not be null").tag()
                + (detail == null || detail.isBlank() ? "" : ": " + detail), cause);
        this.reason = reason;
    }

    /**
     * Returns why the message was not accepted.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }
}
