/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.jwt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.brix.platform.auth.exception.JwtConfigurationException;
import io.runtime.sdk.capability.JwtIssuerCapability;

/**
 * <h2>JWT Issuer Capability — Default RS256 Implementation</h2>
 *
 * <p>Layer 2C platform binding for the {@link JwtIssuerCapability} contract.
 * Migrates the legacy plugin-side {@code JwtTokenService} (B2B2C dual-track
 * methods) into the platform layer where it belongs per blueprint v3.0.9.</p>
 *
 * <h3>Architectural Position</h3>
 * <ul>
 *   <li>Capability contract: {@code io.runtime.sdk.capability.JwtIssuerCapability} (Layer 2A)</li>
 *   <li>Default impl: this class (Layer 2C, {@code platform-auth})</li>
 *   <li>Hosts that supply their own {@code JwtIssuerCapability} bean
 *       suppress this default via {@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}.</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>RS256 (SHA-256 with RSA) signature using an asymmetric key pair.</li>
 *   <li>Payload built via Jackson {@link ObjectMapper} — prevents JWT claim
 *       injection that would be possible with naive string concatenation.</li>
 *   <li>{@code mid} (B-side member) and {@code pid} (C-side principal) are
 *       written into mutually exclusive token kinds — never mixed.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>All configuration is read from {@link JwtProperties} (prefix
 * {@code platform.security.jwt}) at bean construction time:</p>
 * <ul>
 *   <li>{@code private-key-path} — RS256 PKCS#8 PEM (default {@code classpath:keys/private.pem})</li>
 *   <li>{@code issuer} / {@code audience} — standard JWT claims</li>
 *   <li>{@code access-token-expiration-seconds} (default {@code 3600})</li>
 *   <li>{@code identity-token-expiration-seconds} (default {@code 300})</li>
 *   <li>{@code token-version} (default {@code 2})</li>
 * </ul>
 *
 * @since 3.2.0
 */
public class JwtIssuerCapabilityImpl implements JwtIssuerCapability {

    private static final Logger log = LoggerFactory.getLogger(JwtIssuerCapabilityImpl.class);

    // ===== JWT claim keys (mirror JwtValidator) =====
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String CLAIM_TID = "tid";
    private static final String CLAIM_CONTEXT_ID = "cid";
    private static final String CLAIM_MEMBER_AUTHZ_VERSION = "mver";
    private static final String CLAIM_PRINCIPAL_AUTHZ_VERSION = "pver";
    private static final String CLAIM_MEMBER_ID = "mid";
    private static final String CLAIM_MEMBER_TYPE = "mtype";
    private static final String CLAIM_PRINCIPAL_ID = "pid";
    private static final String CLAIM_PRINCIPAL_TYPE = "ptype";
    private static final String CLAIM_ALLOWED_ACTIONS = "allowed_actions";
    private static final String CLAIM_DISPLAY_NAME = "display_name";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_PLATFORM_ROLE = "platform_role";
    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_MFA = "mfa";
    /** Phase 2 / C-4 — claim recording the platform-admin identity that initiated impersonation. */
    private static final String CLAIM_ORIGINAL_SUB = "original_sub";
    // identity-auth 1.0 BRX-IA-SESSION-003 — the authentication event
    private static final String CLAIM_ACR = "acr";
    private static final String CLAIM_AMR = "amr";
    private static final String CLAIM_AUTH_TIME = "auth_time";
    private static final String CLAIM_SID = "sid";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_IDENTITY = "identity";
    private static final String TOKEN_TYPE_BOOTSTRAP_SETUP = "BOOTSTRAP_SETUP";
    private static final String TOKEN_TYPE_MFA_CHALLENGE = "mfa_challenge";

    private static final String ROLE_ACTOR = "actor";
    private static final String ROLE_SUBJECT = "subject";
    private static final String ROLE_PLATFORM_ADMIN = "platform-admin";
    private static final String ROLE_BOOTSTRAP = "bootstrap";
    private static final String PLATFORM_ROLE_BOOTSTRAP = "BOOTSTRAP";

    private static final List<String> IDENTITY_TOKEN_ACTIONS =
            List.of("select_context", "register_tenant", "first_owner_accept");

    private final JwtProperties properties;
    private final ObjectMapper objectMapper;
    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    /** RFC 7638 JWK thumbprint of the signing key, written to every token's {@code kid} header. */
    private final String keyId;
    private final String encodedHeader;

    public JwtIssuerCapabilityImpl(JwtProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.privateKey = loadPrivateKey(properties.getPrivateKeyPath());
        this.publicKey = derivePublicKey(privateKey, properties.getPrivateKeyPath());
        this.keyId = thumbprint(publicKey);
        this.encodedHeader = base64UrlEncode(toJson(
                Map.of("alg", "RS256", "typ", "JWT", "kid", keyId)));
        log.info("[JwtIssuerCapability] RS256 private key loaded from: {} (kid={})",
                properties.getPrivateKeyPath(), keyId);
        log.info("[JwtIssuerCapability] config: issuer={}, audience={}, accessExp={}s, identityExp={}s",
                properties.getIssuer(), properties.getAudience(),
                properties.getAccessTokenExpirationSeconds(),
                properties.getIdentityTokenExpirationSeconds());
    }

    // ==================== Capability methods ====================

    @Override
    public String issueActorAccessToken(ActorTokenRequest request) {
        return signToken(actorClaims(request), properties.getAccessTokenExpirationSeconds());
    }

    @Override
    public IssuedToken issueActorAccessToken(ActorTokenRequest request, AuthenticationContext authentication) {
        Objects.requireNonNull(authentication, "authentication must not be null");
        Map<String, Object> claims = actorClaims(request);
        putAuthentication(claims, authentication);
        return sign(claims, sessionTokenLifetimeSeconds());
    }

    @Override
    public IssuedToken issueOidcIdToken(OidcIdTokenRequest request) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", request.issuer());
        claims.put("sub", request.subject());
        claims.put("aud", request.audience());
        claims.put("azp", request.audience());
        if (request.nonce() != null && !request.nonce().isBlank()) {
            claims.put("nonce", request.nonce());
        }
        putAuthentication(claims, request.authentication());
        claims.putAll(request.additionalClaims());
        return sign(claims, sessionTokenLifetimeSeconds());
    }

    @Override
    public Map<String, Object> publicJwkSet() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("kid", keyId);
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("n", base64UrlUnsigned(publicKey.getModulus()));
        jwk.put("e", base64UrlUnsigned(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(Collections.unmodifiableMap(jwk)));
    }

    private Map<String, Object> actorClaims(ActorTokenRequest request) {
        Map<String, Object> claims = buildBaseClaims(
                String.valueOf(request.identityId()), request.username(), request.email(),
                request.tokenVersion(), JwtIssuerCapability.AUDIENCE_ACTOR, null);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        claims.put(CLAIM_ROLE, ROLE_ACTOR);
        claims.put(CLAIM_SCOPE, ROLE_ACTOR);
        claims.put(CLAIM_TENANT_ID, String.valueOf(request.tenantId()));
        claims.put(CLAIM_TID, String.valueOf(request.tenantId()));
        claims.put(CLAIM_CONTEXT_ID, request.contextId());
        claims.put(CLAIM_MEMBER_ID, String.valueOf(request.memberId()));
        claims.put(CLAIM_MEMBER_TYPE, request.memberType());
        claims.put(CLAIM_MEMBER_AUTHZ_VERSION, request.authzVersion());
        claims.put(CLAIM_ROLES, request.roles() != null ? request.roles() : Collections.emptyList());
        // multi-tenant 5.1 T-30: tenant tokens carry no permission list; the Runtime
        // grant table expands the verified mtype on every request.
        return claims;
    }

    private static void putAuthentication(Map<String, Object> claims, AuthenticationContext authentication) {
        claims.put(CLAIM_ACR, authentication.acr());
        claims.put(CLAIM_AMR, authentication.amr());
        claims.put(CLAIM_AUTH_TIME, authentication.authTime().getEpochSecond());
        claims.put(CLAIM_SID, authentication.sid());
    }

    /** identity-auth BRX-IA-SESSION-001: at most 15 minutes, tighter when configured so. */
    private long sessionTokenLifetimeSeconds() {
        return Math.min(properties.getAccessTokenExpirationSeconds(), MAX_SESSION_ACCESS_TOKEN_SECONDS);
    }

    @Override
    public String issueSubjectAccessToken(SubjectTokenRequest request) {
        Map<String, Object> claims = buildBaseClaims(
                String.valueOf(request.identityId()), request.username(), request.email(),
                request.tokenVersion(), JwtIssuerCapability.AUDIENCE_SUBJECT, null);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        claims.put(CLAIM_ROLE, ROLE_SUBJECT);
        claims.put(CLAIM_SCOPE, ROLE_SUBJECT);
        claims.put(CLAIM_TENANT_ID, String.valueOf(request.tenantId()));
        claims.put(CLAIM_TID, String.valueOf(request.tenantId()));
        claims.put(CLAIM_CONTEXT_ID, request.contextId());
        claims.put(CLAIM_PRINCIPAL_ID, String.valueOf(request.principalId()));
        claims.put(CLAIM_PRINCIPAL_TYPE, request.principalType());
        claims.put(CLAIM_PRINCIPAL_AUTHZ_VERSION, request.authzVersion());
        if (request.displayName() != null) {
            claims.put(CLAIM_DISPLAY_NAME, request.displayName());
        }
        // multi-tenant §8: Subject Tokens have no roles or permissions claims; the
        // Runtime grant table expands the verified ptype on every request.
        return signToken(claims, properties.getAccessTokenExpirationSeconds());
    }

    @Override
    public String issueIdentityToken(IdentityTokenRequest request) {
        // Identity token is a short-lived challenge token; no per-user tv validation.
        Map<String, Object> claims = buildBaseClaims(
                String.valueOf(request.identityId()), request.username(), request.email(),
                properties.getTokenVersion(), JwtIssuerCapability.AUDIENCE_IDENTITY, request.jti());
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_IDENTITY);
        claims.put(CLAIM_SCOPE, TOKEN_TYPE_IDENTITY);
        claims.put(CLAIM_ALLOWED_ACTIONS, IDENTITY_TOKEN_ACTIONS);
        return signToken(claims, properties.getIdentityTokenExpirationSeconds());
    }

    @Override
    public String issuePlatformAdminToken(PlatformAdminTokenRequest request) {
        Map<String, Object> claims = buildBaseClaims(
                String.valueOf(request.identityId()), request.username(), request.email(),
                request.tokenVersion(), properties.getAudience(), null);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        claims.put(CLAIM_ROLE, ROLE_PLATFORM_ADMIN);
        claims.put(CLAIM_SCOPE, "PLATFORM");
        // Platform admin tokens deliberately omit tenant_id / mid / pid:
        // - tenant_id absent → cross-tenant authority enforced by AdminGuard
        // - mid/pid absent → not subject to TenantSqlGuardInterceptor
        claims.put("admin_id", String.valueOf(request.adminId()));
        claims.put("admin_role", request.adminRole());
        // P1-5: Separate platform_role claim — distinct from generic roles list.
        // Extracted by JwtValidator into AuthenticatedUser.platformRole.
        claims.put(CLAIM_PLATFORM_ROLE, request.adminRole());
        claims.put(CLAIM_ROLES, List.of(request.adminRole()));
        claims.put(CLAIM_MFA, "TOTP");
        claims.put(CLAIM_PERMISSIONS, filterFrontendPermissions(request.permissions()));
        return signToken(claims, properties.getAccessTokenExpirationSeconds());
    }

    @Override
    public String issuePlatformMfaChallengeToken(PlatformMfaChallengeTokenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PlatformMfaChallengeTokenRequest must not be null");
        }
        Map<String, Object> claims = buildBaseClaims(
                String.valueOf(request.identityId()), request.username(), request.email(),
                request.tokenVersion(), properties.getAudience(), null);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_MFA_CHALLENGE);
        claims.put(CLAIM_ROLE, ROLE_PLATFORM_ADMIN);
        claims.put(CLAIM_SCOPE, "PLATFORM");
        claims.put("admin_id", String.valueOf(request.adminId()));
        claims.put("admin_role", request.adminRole());
        claims.put(CLAIM_PLATFORM_ROLE, request.adminRole());
        claims.put(CLAIM_MFA, "PENDING");
        claims.put(CLAIM_ROLES, List.of(request.adminRole()));
        claims.put(CLAIM_PERMISSIONS, List.of());
        return signToken(claims, properties.getIdentityTokenExpirationSeconds());
    }

    @Override
    public String issueBootstrapSetupToken(BootstrapSetupTokenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("BootstrapSetupTokenRequest must not be null");
        }
        if (request.jti() == null || request.jti().isBlank()) {
            throw new IllegalArgumentException("bootstrap setup token jti is required");
        }
        long ttlSeconds = Math.max(30L, request.expiresInSeconds());
        Map<String, Object> claims = buildBaseClaims(
                String.valueOf(request.identityId()), request.username(), request.email(),
                request.tokenVersion(), properties.getAudience(), null);
        claims.put("jti", request.jti());
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_BOOTSTRAP_SETUP);
        claims.put(CLAIM_ROLE, ROLE_BOOTSTRAP);
        claims.put(CLAIM_SCOPE, PLATFORM_ROLE_BOOTSTRAP);
        claims.put("admin_id", String.valueOf(request.bootstrapAdminId()));
        claims.put("admin_role", PLATFORM_ROLE_BOOTSTRAP);
        claims.put(CLAIM_PLATFORM_ROLE, PLATFORM_ROLE_BOOTSTRAP);
        claims.put(CLAIM_MFA, "NONE");
        claims.put(CLAIM_ROLES, List.of(PLATFORM_ROLE_BOOTSTRAP));
        claims.put(CLAIM_PERMISSIONS,
                request.permissions() != null && !request.permissions().isEmpty()
                        ? request.permissions()
                        : List.of());
        return signToken(claims, ttlSeconds);
    }

    @Override
    public long getAccessTokenExpirationSeconds() {
        return properties.getAccessTokenExpirationSeconds();
    }

    @Override
    public long getIdentityTokenExpirationSeconds() {
        return properties.getIdentityTokenExpirationSeconds();
    }

    /**
     * Phase 2 / C-4 — Signs a platform-admin viewing token. Preserves
     * {@code role=platform-admin} (and all attendant privileges) but adds
     * {@code tenant_id} (the tenant being viewed) and {@code original_sub}
     * (the platform admin who initiated the view session). Deliberately
     * omits {@code mid} / {@code pid} — this is a tenant-context binding,
     * not impersonation of a specific member/principal.
     */
    @Override
    public String issuePlatformAdminViewToken(PlatformAdminViewTokenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PlatformAdminViewTokenRequest must not be null");
        }
        if (request.viewTenantId() == null) {
            throw new IllegalArgumentException("viewTenantId is required");
        }
        if (request.originalSub() == null || request.originalSub().isBlank()) {
            throw new IllegalArgumentException("originalSub must not be blank");
        }
        if (request.adminRole() == null || request.adminRole().isBlank()) {
            throw new IllegalArgumentException("adminRole must not be blank");
        }

        Map<String, Object> claims = buildBaseClaims(
                String.valueOf(request.identityId()), request.username(), request.email(),
                request.tokenVersion(), properties.getAudience(), null);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        claims.put(CLAIM_ROLE, ROLE_PLATFORM_ADMIN);
        claims.put(CLAIM_SCOPE, "PLATFORM");
        claims.put("admin_id", String.valueOf(request.adminId()));
        claims.put("admin_role", request.adminRole());
        claims.put(CLAIM_PLATFORM_ROLE, request.adminRole());
        claims.put(CLAIM_ROLES, List.of(request.adminRole()));
        claims.put(CLAIM_PERMISSIONS, filterFrontendPermissions(request.permissions()));
        // C-4 specifics — tenant context + originalSub marker.
        claims.put(CLAIM_TENANT_ID, String.valueOf(request.viewTenantId()));
        claims.put(CLAIM_ORIGINAL_SUB, request.originalSub());

        log.info("[JwtIssuerCapability] platform-admin view token issued: originalSub={}, viewTenantId={}, adminRole={}",
                request.originalSub(), request.viewTenantId(), request.adminRole());
        return signToken(claims, properties.getAccessTokenExpirationSeconds());
    }

    static List<String> filterFrontendPermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>(permissions.size());
        for (String permission : permissions) {
            if (permission != null && !"platform:bypass".equals(permission)) {
                filtered.add(permission);
            }
        }
        return List.copyOf(filtered);
    }

    // ==================== Internals ====================

    private Map<String, Object> buildBaseClaims(String sub, String username, String email,
                                                 long tokenVersion, String audience, String jti) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("username", username);
        claims.put("iss", properties.getIssuer());
        claims.put("aud", audience != null ? audience : properties.getAudience());
        claims.put("email", email != null ? email : "");
        // A3: per-user token version — JWT is rejected if DB value has been incremented
        //     (e.g. after password change). Replaces the old static global config value.
        claims.put("tv", tokenVersion);
        claims.put("jti", jti != null && !jti.isBlank() ? jti : UUID.randomUUID().toString());
        return claims;
    }

    private String signToken(Map<String, Object> claims, long expirationSecs) {
        return sign(claims, expirationSecs).value();
    }

    private IssuedToken sign(Map<String, Object> claims, long expirationSecs) {
        long now = System.currentTimeMillis() / 1000L;
        claims.put("iat", now);
        claims.put("nbf", now);
        claims.put("exp", now + expirationSecs);

        String payload = base64UrlEncode(toJson(claims));
        String signingInput = encodedHeader + "." + payload;
        return new IssuedToken(signingInput + "." + rs256Sign(signingInput),
                Instant.ofEpochSecond(now), Instant.ofEpochSecond(now + expirationSecs));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new JwtConfigurationException(
                    "Failed to serialize JWT claims to JSON",
                    properties.getPrivateKeyPath(), e);
        }
    }

    private String rs256Sign(String input) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new JwtConfigurationException(
                    "Failed to sign JWT — RSA SHA256 signing failure",
                    properties.getPrivateKeyPath(), e);
        }
    }

    private static String base64UrlEncode(String content) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64url of the unsigned big-endian bytes, as JWK {@code n} and {@code e} require. */
    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** RFC 7638: SHA-256 over the required members in lexicographic order, without whitespace. */
    private static String thumbprint(RSAPublicKey key) {
        String canonical = "{\"e\":\"" + base64UrlUnsigned(key.getPublicExponent())
                + "\",\"kty\":\"RSA\",\"n\":\"" + base64UrlUnsigned(key.getModulus()) + "\"}";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static RSAPublicKey derivePublicKey(PrivateKey key, String path) {
        if (!(key instanceof RSAPrivateCrtKey crtKey)) {
            throw new JwtConfigurationException(
                    "JWT private key does not carry its public exponent (expected an RSA CRT key)", path, null);
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        } catch (Exception e) {
            throw new JwtConfigurationException("Failed to derive the JWT public key", path, e);
        }
    }

    private static PrivateKey loadPrivateKey(String path) {
        try {
            ResourceLoader loader = new DefaultResourceLoader();
            Resource resource = loader.getResource(path);
            try (InputStream is = resource.getInputStream()) {
                String keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                keyContent = keyContent
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] keyBytes = Base64.getDecoder().decode(keyContent);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
                return KeyFactory.getInstance("RSA").generatePrivate(spec);
            }
        } catch (IOException e) {
            throw new JwtConfigurationException(
                    "Failed to load JWT private key — token issuance disabled", path, e);
        } catch (Exception e) {
            throw new JwtConfigurationException(
                    "Failed to parse JWT private key — invalid PKCS#8 PEM", path, e);
        }
    }
}
