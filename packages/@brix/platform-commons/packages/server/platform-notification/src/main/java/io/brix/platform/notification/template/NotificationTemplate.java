package io.brix.platform.notification.template;

import java.util.List;

/**
 * Parsed notification template definition.
 *
 * @param subject plain-text subject template
 * @param body plain-text body template
 * @param variables declared variable names
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record NotificationTemplate(String subject, String body, List<String> variables) {
}
