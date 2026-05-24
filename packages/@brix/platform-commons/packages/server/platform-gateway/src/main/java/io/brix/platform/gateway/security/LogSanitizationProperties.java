/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * logsanitizeconfiguration
 * <p>
 * used forconfigurationGatewayloginneedsanitizeofsensitivefield，preventsensitiveinformationexposetologin
 * </p>
 * <p>
 * configurationexample（application.yml）：
 * <pre>
 * gateway:
 *   security:
 *     log-sanitization:
 *       enabled: true
 *       sensitive-headers:
 *         - Authorization
 *         - X-API-Key
 *         - X-API-Secret
 *         - Cookie
 *       sensitive-patterns:
 *         - "Bearer\\s+[A-Za-z0-9-_.]+"
 *         - "password\\s*[=:]\\s*\\S+"
 *       mask-char: "*"
 *       visible-chars: 4
 * </pre>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "gateway.security.log-sanitization")
public class LogSanitizationProperties {

    private static final Logger logger = LoggerFactory.getLogger(LogSanitizationProperties.class);

    /**
     * MVP Red Line Requirementssanitizeofdefaultheader
     */
    private static final List<String> DEFAULT_SENSITIVE_HEADERS = List.of(
            "authorization",
            "x-api-key",
            "x-api-secret",
            "cookie",
            "set-cookie",
            "x-auth-token",
            "x-access-token",
            "x-refresh-token",
            "proxy-authorization"
    );

    /**
     * Whether to enable log sanitization
     */
    private boolean enabled = true;

    /**
     * needsanitizeofrequestheaderlist（notdistinguishsizewrite
     */
    private List<String> sensitiveHeaders = new ArrayList<>(DEFAULT_SENSITIVE_HEADERS);

    /**
     * needsanitizeofregexexpressionmodel
     */
    private List<String> sensitivePatterns = new ArrayList<>();

    /**
     * sanitizeuseofmaskcharacter
     */
    private String maskChar = "*";

    /**
     * retaincanseeofcharactercount（beforeaftereachretain
     */
    private int visibleChars = 4;

    /**
     * completeallmaskofminimumlength（shortforthislengthcompletely mask
     */
    private int fullMaskThreshold = 8;

    /**
     * compileafterofregexexpressionmodel
     */
    private List<Pattern> compiledPatterns;

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.warn("[brix]  Log sanitization is DISABLED. " +
                    "Sensitive data may be exposed in logs!");
            return;
        }

        // willallhasheaderconvertissmall
        sensitiveHeaders = sensitiveHeaders.stream()
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());

        // adddefaultofsensitivemodel
        if (sensitivePatterns.isEmpty()) {
            sensitivePatterns = new ArrayList<>(List.of(
                    // Bearer token
                    "Bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]*",
                    // Basic auth
                    "Basic\\s+[A-Za-z0-9+/=]+",
                    // Generic token patterns
                    "token\\s*[=:]\\s*[\"']?[A-Za-z0-9-_]+[\"']?",
                    // Password patterns
                    "password\\s*[=:]\\s*[\"']?[^\\s\"'&]+[\"']?",
                    // API Key patterns
                    "api[_-]?key\\s*[=:]\\s*[\"']?[A-Za-z0-9-_]+[\"']?",
                    // Secret patterns
                    "secret\\s*[=:]\\s*[\"']?[A-Za-z0-9-_]+[\"']?",
                    // MFA and TOTP one-time code patterns
                    "mfa[_-]?secret\\s*[=:]\\s*[\"']?[A-Za-z0-9-_]+[\"']?",
                    "totp(?:Code)?\\s*[=:]\\s*[\"']?[0-9]{6}[\"']?",
                    "otp(?:Code)?\\s*[=:]\\s*[\"']?[0-9]{6}[\"']?",
                    "code\\s*[=:]\\s*[\"']?[0-9]{6}[\"']?",
                    "(?<!\\d)[0-9]{6}(?!\\d)"
            ));
        }

        // compileregextablereach
        compiledPatterns = sensitivePatterns.stream()
                .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());

        logger.info("[brix] Log sanitization enabled for {} header(s) and {} pattern(s)",
                sensitiveHeaders.size(), compiledPatterns.size());
    }

    /**
     * checkspecifyofheaderwhetherneedde-
     *
     * @param headerName requestheadername
     * @return true ifneedde-
     */
    public boolean isSensitiveHeader(String headerName) {
        if (!enabled || headerName == null) {
            return false;
        }
        return sensitiveHeaders.contains(headerName.toLowerCase());
    }

    /**
     * obtainallhassensitiveheadernamecollection（lowercase）
     */
    public Set<String> getSensitiveHeadersAsSet() {
        return Set.copyOf(sensitiveHeaders);
    }

    /**
     * obtaincompileafterofregexexpressionpatterncolumn
     */
    public List<Pattern> getCompiledPatterns() {
        return compiledPatterns != null ? compiledPatterns : List.of();
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getSensitiveHeaders() {
        return sensitiveHeaders;
    }

    public void setSensitiveHeaders(List<String> sensitiveHeaders) {
        this.sensitiveHeaders = sensitiveHeaders;
    }

    public List<String> getSensitivePatterns() {
        return sensitivePatterns;
    }

    public void setSensitivePatterns(List<String> sensitivePatterns) {
        this.sensitivePatterns = sensitivePatterns;
    }

    public String getMaskChar() {
        return maskChar;
    }

    public void setMaskChar(String maskChar) {
        this.maskChar = maskChar;
    }

    public int getVisibleChars() {
        return visibleChars;
    }

    public void setVisibleChars(int visibleChars) {
        this.visibleChars = visibleChars;
    }

    public int getFullMaskThreshold() {
        return fullMaskThreshold;
    }

    public void setFullMaskThreshold(int fullMaskThreshold) {
        this.fullMaskThreshold = fullMaskThreshold;
    }
}
