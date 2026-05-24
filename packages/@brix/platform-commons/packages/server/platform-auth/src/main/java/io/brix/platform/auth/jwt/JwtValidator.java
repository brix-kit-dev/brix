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
package io.brix.platform.auth.jwt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;
import io.brix.platform.auth.exception.JwtConfigurationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.runtime.sdk.capability.IdentityTenantCapability;

/**
 * JWT Validator
 * <p>
 * Validates Token using RS256 public key, does not issue tokens
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class JwtValidator {

    private static final Logger logger = LoggerFactory.getLogger(JwtValidator.class);

    private final JwtProperties properties;
    private final PublicKey publicKey;
    /** Optional — when present, {@code tv} claim is validated against DB per A3. */
    private final IdentityTenantCapability identityTenantCapability;

    public JwtValidator(JwtProperties properties) {
        this(properties, null);
    }

    public JwtValidator(JwtProperties properties, IdentityTenantCapability identityTenantCapability) {
        this.properties = properties;
        this.publicKey = loadPublicKey(properties.getPublicKeyPath());
        this.identityTenantCapability = identityTenantCapability;
    }

    /**
     * Validate Token and parse user information
     *
     * @param token JWT Token
     * @return Authenticated user information
     * @throws JwtValidationException Thrown when validation fails
     */
    public AuthenticatedUser validate(String token) throws JwtValidationException {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("Token is empty", JwtValidationException.Reason.EMPTY);
        }

        // Remove Bearer prefix
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    // P1 Fix: Add Audience validation
                    .requireAudience(properties.getAudience())
                    .clockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return extractUser(claims);

        } catch (ExpiredJwtException e) {
            logger.debug("Token expired: {}", e.getMessage());
            throw new JwtValidationException("Token has expired", JwtValidationException.Reason.EXPIRED);
        } catch (SignatureException e) {
            logger.warn("Invalid token signature: {}", e.getMessage());
            throw new JwtValidationException("Invalid token signature", JwtValidationException.Reason.INVALID_SIGNATURE);
        } catch (MalformedJwtException e) {
            logger.warn("Malformed token: {}", e.getMessage());
            throw new JwtValidationException("Malformed token", JwtValidationException.Reason.MALFORMED);
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported token: {}", e.getMessage());
            throw new JwtValidationException("Unsupported token", JwtValidationException.Reason.UNSUPPORTED);
        } catch (JwtException e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            throw new JwtValidationException("Token validation failed: " + e.getMessage(), 
                    JwtValidationException.Reason.INVALID);
        }
    }

    /**
     * Extract user information from Claims.
     *
     * <p>Phase 2: Supports dual-track claim extraction (Actor/Subject).
     * Enforces mid/pid mutual exclusion constraint.
     *
     * @throws JwtValidationException if mid and pid are both present (constraint violation)
     */
    @SuppressWarnings("unchecked")
    private AuthenticatedUser extractUser(Claims claims) throws JwtValidationException {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId(claims.getSubject());
        user.setTenantId(claims.get("tenant_id", String.class));
        user.setUsername(claims.get("username", String.class));
        user.setEmail(claims.get("email", String.class));
        user.setTokenVersion(claims.get("tv", Long.class));
        user.setJti(claims.get("jti", String.class));
        user.setScope(claims.get("scope", String.class));
        
        // Extract roles and permissions
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List) {
            user.setRoles((List<String>) rolesObj);
        }
        
        Object permissionsObj = claims.get("permissions");
        if (permissionsObj instanceof List) {
            user.setPermissions((List<String>) permissionsObj);
        }

        // ===== Phase 2: 双轨 claim 解析 =====

        // Token type (向后兼容：无 token_type 的旧 Token 视为 ACCESS)
        String tokenTypeStr = claims.get("token_type", String.class);
        user.setTokenType(TokenType.fromValue(tokenTypeStr));

        // Token role (向后兼容：无 role 的旧 Token 视为 ACTOR)
        String roleStr = claims.get("role", String.class);
        user.setTokenRole(TokenRole.fromValue(roleStr));

        // Actor claims (mid/mtype)
        String mid = claims.get("mid", String.class);
        String mtype = claims.get("mtype", String.class);

        // Subject claims (pid/ptype)
        String pid = claims.get("pid", String.class);
        String ptype = claims.get("ptype", String.class);

        // 安全红线 3.1: mid 与 pid 互斥硬约束
        if (mid != null && pid != null) {
            logger.warn("Token contains both mid and pid — mutual exclusion violation, sub={}",
                    claims.getSubject());
            throw new JwtValidationException(
                    "Token contains both mid and pid, which is not allowed",
                    JwtValidationException.Reason.INVALID);
        }

        user.setMemberId(mid);
        user.setMemberType(mtype);
        user.setPrincipalId(pid);
        user.setPrincipalType(ptype);

        // Identity Token: extract allowed_actions
        Object actionsObj = claims.get("allowed_actions");
        if (actionsObj instanceof List) {
            user.setAllowedActions((List<String>) actionsObj);
        }

        // P1-5: platform_role claim — present only for Platform Admin tokens.
        // Drives AuthenticatedUser.isSuperAdmin() and isPlatformAdmin().
        String platformRole = claims.get("platform_role", String.class);
        user.setPlatformRole(platformRole);

        // Phase 2 / C-4: original_sub — present only on impersonation tokens
        // issued via ViewModeCapability.switchTo. Records the platform-admin
        // identity that initiated the impersonation, used by the front-end
        // banner and the audit-log "exit super-admin view" entry.
        String originalSub = claims.get("original_sub", String.class);
        user.setOriginalSub(originalSub);

        // A3: validate per-user token version against DB to detect revoked tokens.
        // Only performed when IdentityTenantCapability is wired (safety net for tests).
        if (identityTenantCapability != null) {
            String subjectId = claims.getSubject();
            Long tokenVersion = claims.get("tv", Long.class);
            if (subjectId != null && tokenVersion != null) {
                try {
                    long identityId = Long.parseLong(subjectId);
                    long currentVersion = identityTenantCapability.getTokenVersion(identityId);
                    if (tokenVersion < currentVersion) {
                        logger.warn("[A3] Token version stale: jwt.tv={}, db.tv={}, identityId={}",
                                tokenVersion, currentVersion, identityId);
                        throw new JwtValidationException(
                                "Token has been invalidated (password was changed)",
                                JwtValidationException.Reason.INVALID);
                    }
                } catch (NumberFormatException e) {
                    // sub is not a numeric identity ID (e.g. OAuth sub) — skip TV check
                } catch (IllegalArgumentException e) {
                    // identity not found in DB — treat as invalid
                    logger.warn("[A3] Identity not found during token-version check: sub={}", subjectId);
                    throw new JwtValidationException(
                            "Identity not found",
                            JwtValidationException.Reason.INVALID);
                }
            }
        }

        return user;
    }

    /**
     * Load RSA public key
     */
    private PublicKey loadPublicKey(String path) {
        try {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            Resource resource = resourceLoader.getResource(path);
            
            try (InputStream is = resource.getInputStream()) {
                String keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                // Remove PEM header/footer and newlines
                keyContent = keyContent
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                
                byte[] keyBytes = Base64.getDecoder().decode(keyContent);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                
                return keyFactory.generatePublic(spec);
            }
        } catch (IOException e) {
            // [R10 Fix] Replace RuntimeException with domain-specific JwtConfigurationException
            // Public key loading failure must prevent application startup in insecure mode
            throw new JwtConfigurationException(
                "Failed to load JWT public key, authentication cannot be initialized", path, e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new JwtConfigurationException(
                "Failed to parse public key - invalid key format or unsupported algorithm", path, e);
        }
    }

    /**
     * JWT Validation Exception
     */
    public static class JwtValidationException extends Exception {
        
        public enum Reason {
            EMPTY, EXPIRED, INVALID_SIGNATURE, MALFORMED, UNSUPPORTED, INVALID, REVOKED
        }
        
        private final Reason reason;

        public JwtValidationException(String message, Reason reason) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
