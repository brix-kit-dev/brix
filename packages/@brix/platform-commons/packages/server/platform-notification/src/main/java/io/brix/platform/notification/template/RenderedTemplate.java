package io.brix.platform.notification.template;

/**
 * Rendered plain-text notification content.
 *
 * @param subject rendered subject
 * @param body rendered body
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record RenderedTemplate(String subject, String body) {
}
