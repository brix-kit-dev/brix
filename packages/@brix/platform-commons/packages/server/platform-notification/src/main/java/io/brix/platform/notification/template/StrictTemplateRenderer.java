package io.brix.platform.notification.template;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.runtime.sdk.capability.NotificationException;

/**
 * Strict renderer for literal notification templates.
 *
 * <p>The renderer supports only {@code {{variableName}}} variable markers. It
 * rejects expressions, missing variables, extra variables, duplicate template
 * declarations, HTML markers and CRLF line endings.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class StrictTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)}}");
    private static final Pattern ANY_MUSTACHE = Pattern.compile("\\{\\{([^}]*)}}");

    /**
     * Renders a template with strict variable matching.
     *
     * @param templateKey stable template key for safe diagnostics
     * @param template parsed template
     * @param variables request variables
     * @return rendered subject and body
     */
    public RenderedTemplate render(
            String templateKey,
            NotificationTemplate template,
            Map<String, String> variables) {
        validateTemplateShape(templateKey, template);
        Set<String> declared = declaredVariables(templateKey, template.variables());
        Set<String> referenced = new HashSet<>();
        referenced.addAll(referencedVariables(template.subject()));
        referenced.addAll(referencedVariables(template.body()));
        if (!declared.equals(referenced) || !declared.equals(variables.keySet())) {
            throw invalid(templateKey);
        }
        return new RenderedTemplate(
                replace(template.subject(), variables, templateKey),
                replace(template.body(), variables, templateKey));
    }

    private static void validateTemplateShape(String templateKey, NotificationTemplate template) {
        if (template == null
                || isBlank(template.subject())
                || isBlank(template.body())
                || template.variables() == null
                || template.variables().isEmpty()
                || containsCr(template.subject())
                || containsCr(template.body())
                || containsHtml(template.subject())
                || containsHtml(template.body())) {
            throw invalid(templateKey);
        }
        rejectExpressions(template.subject(), templateKey);
        rejectExpressions(template.body(), templateKey);
    }

    private static Set<String> declaredVariables(String templateKey, List<String> variables) {
        Set<String> declared = new HashSet<>();
        for (String variable : variables) {
            if (isBlank(variable) || !declared.add(variable)) {
                throw invalid(templateKey);
            }
        }
        return declared;
    }

    private static Set<String> referencedVariables(String value) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private static void rejectExpressions(String value, String templateKey) {
        Matcher matcher = ANY_MUSTACHE.matcher(value);
        while (matcher.find()) {
            if (!PLACEHOLDER.matcher(matcher.group()).matches()) {
                throw invalid(templateKey);
            }
        }
    }

    private static String replace(String value, Map<String, String> variables, String templateKey) {
        String rendered = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        if (rendered.contains("{{") || rendered.contains("}}")) {
            throw invalid(templateKey);
        }
        return rendered;
    }

    private static boolean containsHtml(String value) {
        String lower = value.toLowerCase();
        return lower.contains("<html")
                || lower.contains("</")
                || lower.contains("<a ")
                || lower.contains("<br")
                || lower.contains("<body");
    }

    private static boolean containsCr(String value) {
        return value.indexOf('\r') >= 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static NotificationException invalid(String templateKey) {
        return new NotificationException(
                NotificationException.Code.TEMPLATE_INVALID,
                Map.of("templateKey", templateKey));
    }
}
