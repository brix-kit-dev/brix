package io.runtime.sdk.capability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Immutable request for a managed notification delivery.
 *
 * <p>The request intentionally keeps the raw recipient and variables available
 * to the provider call path, while {@link #toString()} exposes only masked and
 * structural data.</p>
 *
 * @param tenantId target tenant id, or {@code null} for platform-level notifications
 * @param recipientEmail target email address
 * @param templateKey stable notification template key
 * @param locale requested locale tag, or {@code null} to use provider default
 * @param variables template variables
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record NotificationRequest(
        Long tenantId,
        String recipientEmail,
        String templateKey,
        String locale,
        Map<String, String> variables) {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_TEMPLATE_KEY_LENGTH = 96;
    private static final int MAX_VARIABLES = 20;
    private static final int MAX_VARIABLE_VALUE_LENGTH = 4096;
    private static final Pattern TEMPLATE_KEY =
            Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
    private static final Pattern VARIABLE_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern LOCALE =
            Pattern.compile("[a-z]{2}(?:-[A-Z]{2})?");

    /**
     * Validates and normalizes the request.
     */
    public NotificationRequest {
        if (tenantId != null && tenantId <= 0) {
            throw invalid("tenantId");
        }
        recipientEmail = requireEmail(recipientEmail);
        templateKey = requireTemplateKey(templateKey);
        locale = normalizeLocale(locale);
        variables = copyVariables(variables);
    }

    /**
     * Returns the recipient with the local part and domain masked.
     *
     * @return masked email address
     */
    public String maskedRecipientEmail() {
        return maskEmail(recipientEmail);
    }

    @Override
    public String toString() {
        return "NotificationRequest[tenantId=" + tenantId
                + ", recipientEmail=" + maskedRecipientEmail()
                + ", templateKey=" + templateKey
                + ", locale=" + locale
                + ", variableKeys=" + variables.keySet()
                + "]";
    }

    private static String requireEmail(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() > MAX_EMAIL_LENGTH || containsControl(trimmed)) {
            throw invalid("recipientEmail");
        }
        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1) {
            throw invalid("recipientEmail");
        }
        return trimmed;
    }

    private static String requireTemplateKey(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null
                || trimmed.length() > MAX_TEMPLATE_KEY_LENGTH
                || containsControl(trimmed)
                || !TEMPLATE_KEY.matcher(trimmed).matches()) {
            throw invalid("templateKey");
        }
        return trimmed;
    }

    private static String normalizeLocale(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (containsControl(trimmed) || !LOCALE.matcher(trimmed).matches()) {
            throw invalid("locale");
        }
        return trimmed;
    }

    private static Map<String, String> copyVariables(Map<String, String> source) {
        if (source == null || source.isEmpty() || source.size() > MAX_VARIABLES) {
            throw invalid("variables");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (key == null || value == null || !VARIABLE_NAME.matcher(key).matches()) {
                throw invalid("variables");
            }
            if (containsControl(value) || value.length() > MAX_VARIABLE_VALUE_LENGTH) {
                throw invalid("variables");
            }
            copy.put(key, value);
        }
        return Map.copyOf(copy);
    }

    private static NotificationException invalid(String field) {
        return new NotificationException(
                NotificationException.Code.REQUEST_INVALID,
                Map.of("field", field));
    }

    private static boolean containsControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String localMask = local.substring(0, 1) + "***";
        int dot = domain.lastIndexOf('.');
        String domainMask = dot > 0
                ? domain.substring(0, 1) + "***" + domain.substring(dot)
                : domain.substring(0, 1) + "***";
        return localMask + "@" + domainMask;
    }
}
