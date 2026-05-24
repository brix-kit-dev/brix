package io.brix.platform.tenant.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Guards audit payloads against accidental persistence of credentials and one-time secrets.
 */
final class AuditSensitiveDataGuard {

    private static final String REDACTED = "[REDACTED]";

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(?:password|passwd|pwd|token|setup[_-]?token|secret|mfa[_-]?secret|totp(?:Code)?|otp(?:Code)?|code)\\b\\s*=\\s*[^\\s,;}&]+"),
            Pattern.compile("(?i)\\\"(?:password|temppassword|setup_token|setuptoken|setupurl|setupurlmasked|mfa_secret|mfasecret|totpcode|otpcode|token|secret|code)\\\"\\s*:\\s*\\\"[^\\\"]+\\\""),
            Pattern.compile("(?i)(?:setup_token|setuptoken|setupurl|setupurlmasked|token|code)=[^\\s&]+"),
            Pattern.compile("(?<!\\d)\\d{6}(?!\\d)")
    );

    private AuditSensitiveDataGuard() {
    }

    static void assertSafe(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(value).find()) {
                throw new IllegalArgumentException(
                        "Audit field '" + fieldName + "' contains sensitive data and must not be persisted");
            }
        }
    }

    static String scrubForLog(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String scrubbed = value;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            scrubbed = pattern.matcher(scrubbed).replaceAll(REDACTED);
        }
        return scrubbed;
    }
}