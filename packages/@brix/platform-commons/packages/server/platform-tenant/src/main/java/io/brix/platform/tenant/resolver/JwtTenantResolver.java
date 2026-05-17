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
package io.brix.platform.tenant.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Tenant resolver that extracts tenant ID from JWT token claims.
 * 
 * <p>This resolver parses the JWT token from the Authorization header and
 * extracts the tenant ID from the 'tid' (tenant ID) claim. JWT-based
 * resolution has the highest priority as it's cryptographically signed.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>JWT Structure Expected</h3>
 * <pre>{@code
 * {
 *   "sub": "user-123",
 *   "tenant_id": "tenant-456",    // <-- Tenant ID claim
 *   "exp": 1735689600,
 *   "iat": 1735603200
 * }
 * }</pre>
 * 
 * <h3>Security Model</h3>
 * <ul>
 *   <li>This resolver only PARSES the JWT, it does NOT validate signatures</li>
 *   <li>JWT signature validation is handled by the security filter chain</li>
 *   <li>By the time this resolver runs, the JWT is already validated</li>
 *   <li>This separation of concerns follows the Single Responsibility Principle</li>
 * </ul>
 * 
 * <h3>Configuration</h3>
 * <p>The claim name defaults to "tenant_id" which aligns with the JWT issued
 * by JwtTokenService in identity-core. It can be customized via constructor:</p>
 * <pre>{@code
 * // Default: extracts "tenant_id" claim (aligned with JwtTokenService)
 * JwtTenantResolver resolver = new JwtTenantResolver();
 * 
 * // Custom: extracts a different claim
 * JwtTenantResolver resolver = new JwtTenantResolver("tid");
 * }</pre>
 * 
 * <h3>Priority</h3>
 * <p>Priority 0 (highest) - JWT claims take precedence over other sources
 * because they are cryptographically signed and verified.</p>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantResolver
 * @see TenantResolverChain
 */
public class JwtTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtTenantResolver.class);

    /**
     * Standard Authorization header name.
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Bearer token prefix.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Default tenant ID claim name in JWT.
     *
     * <p>Aligned with JwtTokenService (identity-core) which issues JWT tokens
     * with {@code "tenant_id"} as the tenant claim key. Previous value was "tid"
     * which caused a mismatch — the token contained "tenant_id" but the resolver
     * was looking for "tid", so tenant resolution always failed.</p>
     *
     * <p><b>Phase 1.1 Fix</b>: Changed from "tid" to "tenant_id" to match the
     * actual JWT payload structure issued by JwtTokenService and parsed by
     * JwtValidator in platform-auth.</p>
     */
    public static final String DEFAULT_TENANT_CLAIM = "tenant_id";

    /**
     * The name of the JWT claim containing the tenant ID.
     */
    private final String tenantClaimName;

    /**
     * Jackson ObjectMapper for JSON parsing.
     * Note: ObjectMapper is thread-safe and should be reused.
     */
    private final ObjectMapper objectMapper;

    /**
     * Creates a JwtTenantResolver with default claim name "tid".
     */
    public JwtTenantResolver() {
        this(DEFAULT_TENANT_CLAIM);
    }

    /**
     * Creates a JwtTenantResolver with a custom claim name.
     * 
     * @param tenantClaimName the name of the JWT claim containing tenant ID
     * @throws IllegalArgumentException if tenantClaimName is null or blank
     */
    public JwtTenantResolver(String tenantClaimName) {
        this(tenantClaimName, new ObjectMapper());
    }

    /**
     * Creates a JwtTenantResolver with custom claim name and ObjectMapper.
     * 
     * @param tenantClaimName the name of the JWT claim containing tenant ID
     * @param objectMapper the Jackson ObjectMapper for JSON parsing
     * @throws IllegalArgumentException if any parameter is null or blank
     */
    public JwtTenantResolver(String tenantClaimName, ObjectMapper objectMapper) {
        if (tenantClaimName == null || tenantClaimName.isBlank()) {
            throw new IllegalArgumentException("Tenant claim name cannot be null or blank");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper cannot be null");
        }
        this.tenantClaimName = tenantClaimName;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves tenant ID from JWT token in Authorization header.
     * 
     * <p>Resolution process:</p>
     * <ol>
     *   <li>Extract Bearer token from Authorization header</li>
     *   <li>Parse JWT payload (middle part, Base64 decoded)</li>
     *   <li>Extract tenant ID from configured claim</li>
     * </ol>
     * 
     * @param request the HTTP servlet request
     * @return Optional containing tenant ID if found, empty otherwise
     */
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("No Bearer token found in Authorization header");
            return Optional.empty();
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        
        return extractTenantFromToken(token);
    }

    /**
     * Extracts tenant ID from JWT token payload.
     * 
     * <p>JWT structure: header.payload.signature</p>
     * <p>We only need to decode the payload (middle part) to extract claims.</p>
     * 
     * @param token the JWT token string
     * @return Optional containing tenant ID if found, empty otherwise
     */
    private Optional<String> extractTenantFromToken(String token) {
        String[] parts = token.split("\\.");
        
        // JWT must have exactly 3 parts: header.payload.signature
        if (parts.length != 3) {
            log.warn("Invalid JWT format: expected 3 parts, got {}", parts.length);
            return Optional.empty();
        }

        try {
            // Decode the payload (second part)
            String payload = decodeBase64Url(parts[1]);
            
            // Parse JSON payload
            JsonNode claims = objectMapper.readTree(payload);
            
            // Extract tenant claim
            JsonNode tenantNode = claims.get(tenantClaimName);
            
            if (tenantNode == null || tenantNode.isNull()) {
                log.debug("Tenant claim '{}' not found in JWT", tenantClaimName);
                return Optional.empty();
            }

            String tenantId = tenantNode.asText();
            
            if (tenantId.isBlank()) {
                log.debug("Tenant claim '{}' is blank in JWT", tenantClaimName);
                return Optional.empty();
            }

            log.debug("Resolved tenant '{}' from JWT claim '{}'", tenantId, tenantClaimName);
            return Optional.of(tenantId);

        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode JWT payload: {}", e.getMessage());
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JWT payload as JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Decodes a Base64 URL-encoded string.
     * 
     * <p>JWT uses Base64 URL encoding (RFC 4648), which differs from standard
     * Base64 in that it uses '-' and '_' instead of '+' and '/', and may
     * omit padding characters.</p>
     * 
     * @param encoded the Base64 URL-encoded string
     * @return the decoded string
     * @throws IllegalArgumentException if decoding fails
     */
    private String decodeBase64Url(String encoded) {
        // Base64 URL decoder handles the URL-safe alphabet automatically
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * Checks if the request contains an Authorization header with Bearer token.
     * 
     * @param request the HTTP servlet request
     * @return true if Bearer token is present, false otherwise
     */
    @Override
    public boolean supports(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        return authHeader != null && authHeader.startsWith(BEARER_PREFIX);
    }

    /**
     * Returns the highest priority (0) since JWT is the most trusted source.
     * 
     * @return 0 (highest priority)
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * Returns the resolver name for logging.
     * 
     * @return "JwtTenantResolver"
     */
    @Override
    public String getName() {
        return "JwtTenantResolver[claim=" + tenantClaimName + "]";
    }
}
