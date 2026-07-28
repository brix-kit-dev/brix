package io.brix.platform.notification.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.NotificationException;

class StrictTemplateRendererTest {

    private final StrictTemplateRenderer renderer = new StrictTemplateRenderer();

    @Test
    void rejectsExpressionPlaceholders() {
        NotificationTemplate template = new NotificationTemplate(
                "Hello",
                "Use {{ setupUrl.toString() }}",
                List.of("setupUrl"));

        assertThrows(NotificationException.class, () -> renderer.render(
                "platform.admin.setup.initial",
                template,
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret")));
    }

    @Test
    void rejectsHtml() {
        NotificationTemplate template = new NotificationTemplate(
                "Hello",
                "<html>{{setupUrl}}</html>",
                List.of("setupUrl"));

        assertThrows(NotificationException.class, () -> renderer.render(
                "platform.admin.setup.initial",
                template,
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret")));
    }

    @Test
    void rejectsCrLf() {
        NotificationTemplate template = new NotificationTemplate(
                "Hello",
                "Use {{setupUrl}}\r\n",
                List.of("setupUrl"));

        assertThrows(NotificationException.class, () -> renderer.render(
                "platform.admin.setup.initial",
                template,
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret")));
    }

    @Test
    void rendersLiteralPlaceholder() {
        RenderedTemplate rendered = renderer.render(
                "platform.admin.setup.initial",
                new NotificationTemplate("Hello", "Use {{setupUrl}}", List.of("setupUrl")),
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret"));

        assertEquals("Use https://setup.example.invalid/token/raw-secret", rendered.body());
    }
}
