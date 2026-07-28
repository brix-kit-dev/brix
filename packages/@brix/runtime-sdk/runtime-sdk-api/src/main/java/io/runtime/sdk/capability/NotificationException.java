package io.runtime.sdk.capability;

import java.util.Map;

import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCode;

/**
 * Stable exception for notification capability failures.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class NotificationException extends BrixException {

    /**
     * Notification error catalogue.
     */
    public enum Code implements ErrorCode {
        REQUEST_INVALID("notification.request.invalid"),
        TEMPLATE_NOT_FOUND("notification.template.not_found"),
        TEMPLATE_INVALID("notification.template.invalid"),
        DELIVERY_UNAVAILABLE("notification.delivery.unavailable"),
        DELIVERY_FAILED("notification.delivery.failed");

        private final String wireCode;

        Code(String wireCode) {
            this.wireCode = wireCode;
        }

        @Override
        public String wireCode() {
            return wireCode;
        }
    }

    /**
     * Creates a notification exception.
     *
     * @param errorCode stable notification error code
     * @param safeParameters non-sensitive parameters safe for external envelopes
     * @param cause internal cause
     */
    public NotificationException(Code errorCode, Map<String, String> safeParameters, Throwable cause) {
        super(errorCode, safeParameters, cause);
    }

    /**
     * Creates a notification exception without an internal cause.
     *
     * @param errorCode stable notification error code
     * @param safeParameters non-sensitive parameters safe for external envelopes
     */
    public NotificationException(Code errorCode, Map<String, String> safeParameters) {
        this(errorCode, safeParameters, null);
    }
}
