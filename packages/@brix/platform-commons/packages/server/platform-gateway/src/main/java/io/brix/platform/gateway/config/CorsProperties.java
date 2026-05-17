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
package io.brix.platform.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * CORS (Cross-Origin Resource Sharing) whitelist configuration properties.
 *
 * <p>Reads CORS configuration from application.yml, supporting runtime
 * configuration via environment variables without code changes.</p>
 *
 * <h3>Configuration Examples</h3>
 * <pre>{@code
 * # Option 1: YAML list configuration
 * gateway:
 *   cors:
 *     allowed-origin-patterns:
 *       - "https://www.your-domain.com"
 *       - "https://*.your-domain.com"
 *
 * # Option 2: Environment variable (comma-separated)
 * GATEWAY_ALLOWED_ORIGINS=https://www.example.com,https://*.example.com
 *
 * # Option 3: YAML referencing environment variable
 * gateway:
 *   cors:
 *     allowed-origin-patterns: ${GATEWAY_ALLOWED_ORIGINS:*}
 * }</pre>
 *
 * <h3>Production Security Requirements</h3>
 * <ul>
 *   <li>Production MUST configure specific domain whitelist; wildcard "*" is forbidden</li>
 *   <li>Enable {@code warn-on-wildcard} to detect insecure configuration at startup</li>
 *   <li>Set {@code block-wildcard-in-production=true} to prevent application startup
 *       with wildcard origins in production environment</li>
 *   <li>Restrict {@code allowed-methods} and {@code allowed-headers} to actual needs</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @version 2.0.0 (Phase 5.5 — Production Hardening)
 * @see CorsConfig CORS filter configuration class
 */
@ConfigurationProperties(prefix = "gateway.cors")
public class CorsProperties {
    
    /**
     * Allowed origin pattern list
     * <p>
     * Supports wildcard patterns, for example:
     * <ul>
     *   <li>"https://www.example.com" - exact match</li>
     *   <li>"https://*.example.com" - match all subdomains</li>
     *   <li>"*" - allow all origins (warning: development only)</li>
     * </ul>
     * </p>
     * <p>
     * Supports two configuration methods:
     * <ol>
     *   <li>YAML list format</li>
     *   <li>Comma-separated string (for environment variables, e.g. GATEWAY_ALLOWED_ORIGINS)</li>
     * </ol>
     * </p>
     */
    private List<String> allowedOriginPatterns = List.of("*");
    
    /**
     * Raw allowed origins configuration string
     * <p>
     * For internal use, handles comma-separated format from environment variables.
     * </p>
     */
    private String allowedOriginPatternsRaw;
    
    /**
     * Allowed HTTP methods list
     * <p>
     * Common values: GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD
     * </p>
     */
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    
    /**
     * Allowed request headers list
     * <p>
     * Use "*" to allow all request headers, or specify specific header names.
     * Common headers: Content-Type, Authorization, X-Requested-With
     * </p>
     */
    private List<String> allowedHeaders = List.of("*");
    
    /**
     * Response headers exposed to clients
     * <p>
     * By default, browsers can only access these response headers:
     * Cache-Control, Content-Language, Content-Type, Expires, Last-Modified, Pragma.
     * Configure here to expose additional response headers.
     * </p>
     */
    private List<String> exposedHeaders = List.of();
    
    /**
     * Whether to allow credentials (Cookie, Authorization, etc.)
     * <p>
     * When set to true, allowedOriginPatterns cannot use "*" (browser security restriction).
     * </p>
     */
    private boolean allowCredentials = true;
    
    /**
     * Preflight request (OPTIONS) cache duration in seconds
     * <p>
     * Browser will not send duplicate preflight requests within this time.
     * Recommended value: 3600 (1 hour)
     * </p>
     */
    private long maxAge = 3600L;
    
    /**
     * Whether to warn about wildcard configuration at startup
     * <p>
     * When enabled, a security warning will be logged at startup if allowedOriginPatterns contains "*".
     * Recommended to keep enabled in production.
     * </p>
     */
    private boolean warnOnWildcard = true;
    
    /**
     * Whether to block wildcard configuration in production
     * <p>
     * When enabled, if current environment is production and allowedOriginPatterns contains "*",
     * an exception will be thrown to prevent application startup.
     * </p>
     */
    private boolean blockWildcardInProduction = false;

    // ========== Getters and Setters ==========
    
    /**
     * Get allowed origin pattern list
     * <p>
     * If configured with comma-separated string (e.g. environment variable), will be automatically parsed to list.
     * </p>
     * 
     * @return parsed allowed origins list
     */
    public List<String> getAllowedOriginPatterns() {
        // If raw string configuration exists, parse to list
        if (StringUtils.hasText(allowedOriginPatternsRaw)) {
            return parseCommaSeparatedOrigins(allowedOriginPatternsRaw);
        }
        return allowedOriginPatterns;
    }

    /**
     * Set allowed origin pattern list
     * 
     * @param allowedOriginPatterns origin pattern list
     */
    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        // Check if single-element list contains comma (parsed from environment variable)
        if (allowedOriginPatterns != null && allowedOriginPatterns.size() == 1) {
            String singleValue = allowedOriginPatterns.get(0);
            if (singleValue != null && singleValue.contains(",")) {
                // This is comma-separated environment variable
                this.allowedOriginPatterns = parseCommaSeparatedOrigins(singleValue);
                return;
            }
        }
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
    
    /**
     * Set raw origins configuration string
     * <p>
     * For receiving comma-separated values directly from environment variables.
     * </p>
     * 
     * @param allowedOriginPatternsRaw comma-separated origins string
     */
    public void setAllowedOriginPatternsRaw(String allowedOriginPatternsRaw) {
        this.allowedOriginPatternsRaw = allowedOriginPatternsRaw;
    }
    
    /**
     * Parse comma-separated origins string
     * 
     * @param origins comma-separated origins string
     * @return parsed origins list
     */
    private List<String> parseCommaSeparatedOrigins(String origins) {
        if (!StringUtils.hasText(origins)) {
            return List.of("*");
        }
        
        List<String> result = new ArrayList<>();
        String[] parts = origins.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.hasText(trimmed)) {
                result.add(trimmed);
            }
        }
        
        return result.isEmpty() ? List.of("*") : result;
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    public boolean isWarnOnWildcard() {
        return warnOnWildcard;
    }

    public void setWarnOnWildcard(boolean warnOnWildcard) {
        this.warnOnWildcard = warnOnWildcard;
    }

    public boolean isBlockWildcardInProduction() {
        return blockWildcardInProduction;
    }

    public void setBlockWildcardInProduction(boolean blockWildcardInProduction) {
        this.blockWildcardInProduction = blockWildcardInProduction;
    }

    /**
     * Check if wildcard configuration is present
     * 
     * @return true if allowedOriginPatterns contains "*"
     */
    public boolean hasWildcardOrigin() {
        return allowedOriginPatterns != null && allowedOriginPatterns.contains("*");
    }

    @Override
    public String toString() {
        return "CorsProperties{" +
                "allowedOriginPatterns=" + allowedOriginPatterns +
                ", allowedMethods=" + allowedMethods +
                ", allowedHeaders=" + allowedHeaders +
                ", exposedHeaders=" + exposedHeaders +
                ", allowCredentials=" + allowCredentials +
                ", maxAge=" + maxAge +
                ", warnOnWildcard=" + warnOnWildcard +
                ", blockWildcardInProduction=" + blockWildcardInProduction +
                '}';
    }
}
