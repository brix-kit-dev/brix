package io.brix.platform.notification.delivery;

import java.util.Objects;

/**
 * Rendered plain-text email message passed to a delivery adapter.
 *
 * @param recipientEmail target recipient
 * @param subject rendered plain-text subject
 * @param body rendered plain-text body
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record EmailMessage(String recipientEmail, String subject, String body) {

    /**
     * Validates the rendered email message.
     */
    public EmailMessage {
        recipientEmail = requireNonBlank(recipientEmail, "recipientEmail");
        subject = requireNonBlank(subject, "subject");
        body = requireNonBlank(body, "body");
    }

    @Override
    public String toString() {
        return "EmailMessage[recipientEmail=***, subjectLength=" + subject.length()
                + ", bodyLength=" + body.length() + "]";
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return trimmed;
    }
}
