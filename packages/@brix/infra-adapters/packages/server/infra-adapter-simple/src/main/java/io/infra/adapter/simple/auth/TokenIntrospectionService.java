/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.simple.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAuth 2.0 Token Introspection Service
 * 
 * <p>Handles token validation via OAuth 2.0 Token Introspection endpoint
 * with caching support to minimize remote calls.</p>
 * 
 * <h3>Architecture Note</h3>
 * <p>Extracted from DelegatedAuthContextCapability to separate token validation
 * concerns from authentication context management. This follows the Single
 * Responsibility Principle.</p>
 * 
 * <p><b>Technical Notes:</b>
 * This token validation service is extracted from DelegatedAuthContextCapability.
 * It is responsible for calling the OAuth 2.0 Introspection endpoint to validate
 * token validity. Includes local cache to reduce remote calls.</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see DelegatedPrincipal
 */
public class TokenIntrospectionService {

    private static final Logger log = LoggerFactory.getLogger(TokenIntrospectionService.class);

    private final HttpClient httpClient;
    private final String tokenValidationUrl;
    private final String clientId;
    private final String clientSecret;
    private final Duration cacheTtl;

    /**
     * Token validation result cache (Token -> CachedAuthInfo)
     */
    private final Map<String, CachedAuthInfo> authCache = new ConcurrentHashMap<>();

    /**
     * Creates a new TokenIntrospectionService.
     *
     * @param tokenValidationUrl OAuth 2.0 Introspection endpoint URL
     * @param clientId OAuth client ID
     * @param clientSecret OAuth client secret
     * @param cacheTtl Cache time-to-live duration
     */
    public TokenIntrospectionService(
            String tokenValidationUrl,
            String clientId,
            String clientSecret,
            Duration cacheTtl) {
        this.tokenValidationUrl = Objects.requireNonNull(tokenValidationUrl,
                "tokenValidationUrl cannot be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret cannot be null");
        this.cacheTtl = cacheTtl != null ? cacheTtl : Duration.ofMinutes(5);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        log.info("TokenIntrospectionService initialized: endpoint={}, cacheTtl={}", 
                tokenValidationUrl, this.cacheTtl);
    }

    /**
     * Validates a token and returns the associated principal.
     *
     * <p>First checks the local cache. If not found or expired,
     * calls the remote introspection endpoint.</p>
     * 
     * <p><b>Validation Flow:</b></p>
     * <ol>
     *   <li>Check local cache</li>
     *   <li>If cache miss, call remote SSO</li>
     *   <li>Parse response and cache result</li>
     * </ol>
     *
     * @param token Bearer token to validate (without "Bearer " prefix)
     * @return DelegatedPrincipal if valid, null otherwise
     */
    public DelegatedPrincipal validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        // Check cache
        CachedAuthInfo cached = authCache.get(token);
        if (cached != null && !cached.isExpired()) {
            log.debug("Token validation cache hit");
            return cached.getPrincipal();
        }

        // Call remote SSO
        try {
            DelegatedPrincipal principal = callTokenIntrospection(token);
            
            // Cache result
            if (principal != null) {
                authCache.put(token, new CachedAuthInfo(principal, cacheTtl));
                log.debug("Token validated successfully: userId={}", principal.getName());
            }
            
            return principal;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Calls the OAuth 2.0 Token Introspection endpoint.
     *
     * @param token Bearer token
     * @return DelegatedPrincipal if valid
     * @throws Exception if the call fails
     */
    private DelegatedPrincipal callTokenIntrospection(String token) throws Exception {
        // Build request body
        String requestBody = String.format("token=%s&client_id=%s&client_secret=%s",
                token, clientId, clientSecret);

        // Build HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenValidationUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        // Send request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Token introspection returned status: {}", response.statusCode());
            return null;
        }

        // Parse response
        return parseIntrospectionResponse(response.body());
    }

    /**
     * Parses the Token Introspection response.
     *
     * <p>Expected response format:</p>
     * <pre>{@code
     * {
     *   "active": true,
     *   "sub": "user123",
     *   "username": "john.doe",
     *   "scope": "read write",
     *   "roles": ["ADMIN", "USER"],
     *   "tenant_id": "tenant001"
     * }
     * }</pre>
     *
     * @param responseBody Response body JSON
     * @return DelegatedPrincipal if active, null otherwise
     */
    private DelegatedPrincipal parseIntrospectionResponse(String responseBody) {
        // Check if token is active
        if (!responseBody.contains("\"active\":true") && !responseBody.contains("\"active\": true")) {
            log.debug("Token is inactive or invalid");
            return null;
        }

        // Extract fields (simplified implementation - production should use Jackson/Gson)
        String userId = extractJsonField(responseBody, "sub");
        String username = extractJsonField(responseBody, "username");
        String tenantId = extractJsonField(responseBody, "tenant_id");

        return DelegatedPrincipal.builder()
                .userId(userId != null ? userId : "unknown")
                .username(username != null ? username : userId)
                .tenantId(tenantId)
                .permissions(extractJsonArray(responseBody, "permissions"))
                .roles(extractJsonArray(responseBody, "roles"))
                .build();
    }

    /**
     * Extracts a string field value from JSON (simplified implementation).
     */
    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extracts an array field from JSON (simplified implementation).
     */
    private Set<String> extractJsonArray(String json, String field) {
        Set<String> result = new HashSet<>();
        String pattern = "\"" + field + "\"\\s*:\\s*\\[([^\\]]+)\\]";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            String array = m.group(1);
            String[] items = array.split(",");
            for (String item : items) {
                String trimmed = item.trim().replaceAll("\"", "");
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    /**
     * Clears the token cache.
     * 
     * <p>Should be called periodically or when security events occur.</p>
     */
    public void clearCache() {
        authCache.clear();
        log.info("Token cache cleared");
    }

    /**
     * Invalidates a specific token from cache.
     *
     * @param token Token to invalidate
     */
    public void invalidateToken(String token) {
        authCache.remove(token);
        log.debug("Token invalidated from cache");
    }

    // ==================== Inner Classes ====================

    /**
     * Cached authentication information with expiration.
     */
    private static class CachedAuthInfo {
        private final DelegatedPrincipal principal;
        private final long expireTime;

        CachedAuthInfo(DelegatedPrincipal principal, Duration ttl) {
            this.principal = principal;
            this.expireTime = System.currentTimeMillis() + ttl.toMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        DelegatedPrincipal getPrincipal() {
            return principal;
        }
    }
}
