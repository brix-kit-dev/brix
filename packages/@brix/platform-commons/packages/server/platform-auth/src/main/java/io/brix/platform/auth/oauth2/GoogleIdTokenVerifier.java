/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.oauth2;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.HttpCapability.HttpResult;

/**
 * <h2>Google ID Token Verifier</h2>
 *
 * <p>Verifies the signature and claims of a Google-issued OpenID Connect ID Token
 * using RS256 + Google's published JWKS. This is a pure cryptographic component
 * — no database / capability dependencies beyond {@link HttpCapability}.</p>
 *
 * <p>Migrated from {@code app-identity} (D2) into the platform layer so that
 * {@code OAuth2FederationCapability} can be implemented without reaching into
 * the plugin.</p>
 *
 * @since 3.2.0
 */
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER_HTTPS = "https://accounts.google.com";
    private static final String ISSUER_SHORT = "accounts.google.com";
    private static final long JWKS_CACHE_TTL_MS = 3_600_000L;
    private static final long CLOCK_SKEW_SECONDS = 300L;

    private final HttpCapability httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, PublicKey> jwksCache = new ConcurrentHashMap<>();
    private final AtomicLong lastJwksRefresh = new AtomicLong(0L);

    public GoogleIdTokenVerifier(HttpCapability httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Verify the supplied Google ID Token and return parsed user info.
     *
     * @param idToken  raw JWT string
     * @param clientId expected audience (Google OAuth2 client id)
     * @return parsed Google user info (sub / email / name / picture / email_verified)
     * @throws OAuth2Exception with code {@code OAUTH2_INVALID_ID_TOKEN} on any failure
     */
    public GoogleUserInfo verify(String idToken, String clientId) {
        if (idToken == null || idToken.isBlank()) {
            throw invalid("ID token is empty");
        }
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw invalid("Invalid JWT format");
        }
        try {
            JsonNode header = objectMapper.readTree(decodeBase64Url(parts[0]));
            String alg = header.path("alg").asText();
            if (!"RS256".equals(alg)) {
                throw invalid("Unsupported algorithm: " + alg + ". Expected RS256.");
            }
            String kid = header.path("kid").asText();
            if (kid.isEmpty()) {
                throw invalid("Missing kid in header");
            }

            PublicKey publicKey = getPublicKey(kid);
            verifySignature(parts[0] + "." + parts[1], parts[2], publicKey);

            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(decodeBase64Url(parts[1]), Map.class);
            verifyClaims(claims, clientId);
            return GoogleUserInfo.fromClaims(claims);
        } catch (OAuth2Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Google ID token", e);
            throw invalid(e.getMessage());
        }
    }

    private void verifyClaims(Map<String, Object> claims, String clientId) {
        String iss = (String) claims.get("iss");
        if (!ISSUER_HTTPS.equals(iss) && !ISSUER_SHORT.equals(iss)) {
            throw invalid("Invalid issuer: " + iss);
        }
        String aud = (String) claims.get("aud");
        if (!clientId.equals(aud)) {
            throw invalid("Audience mismatch. Expected: " + clientId + ", got: " + aud);
        }
        long now = System.currentTimeMillis() / 1000L;
        Number exp = (Number) claims.get("exp");
        if (exp == null) {
            throw invalid("Missing exp claim");
        }
        if (exp.longValue() + CLOCK_SKEW_SECONDS < now) {
            throw invalid("Token has expired");
        }
        Number iat = (Number) claims.get("iat");
        if (iat != null && iat.longValue() - CLOCK_SKEW_SECONDS > now) {
            throw invalid("Token issued in the future");
        }
    }

    private void verifySignature(String signingInput, String signatureB64, PublicKey publicKey) {
        try {
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureB64);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!sig.verify(signatureBytes)) {
                throw invalid("Signature verification failed");
            }
        } catch (OAuth2Exception e) {
            throw e;
        } catch (Exception e) {
            throw invalid("Signature verification error: " + e.getMessage());
        }
    }

    private PublicKey getPublicKey(String kid) {
        PublicKey cached = jwksCache.get(kid);
        if (cached != null && !isJwksCacheExpired()) {
            return cached;
        }
        synchronized (this) {
            cached = jwksCache.get(kid);
            if (cached != null && !isJwksCacheExpired()) {
                return cached;
            }
            refreshJwksCache();
        }
        PublicKey key = jwksCache.get(kid);
        if (key == null) {
            throw invalid("Public key not found for kid: " + kid);
        }
        return key;
    }

    private void refreshJwksCache() {
        try {
            log.info("Refreshing Google JWKS cache from {}", GOOGLE_JWKS_URL);
            HttpResult response = httpClient.get(GOOGLE_JWKS_URL,
                    Map.of("Accept", "application/json"));
            if (response.statusCode() != 200) {
                throw new OAuth2Exception(
                        "JWKS fetch failed with HTTP status " + response.statusCode(),
                        "OAUTH2_JWKS_FAILURE");
            }
            JsonNode jwks = objectMapper.readTree(response.body());
            JsonNode keys = jwks.path("keys");
            if (!keys.isArray()) {
                throw new OAuth2Exception(
                        "Invalid JWKS response: 'keys' field is not an array",
                        "OAUTH2_JWKS_FAILURE");
            }
            ConcurrentHashMap<String, PublicKey> newCache = new ConcurrentHashMap<>();
            for (JsonNode keyNode : keys) {
                String kid = keyNode.path("kid").asText();
                String kty = keyNode.path("kty").asText();
                String alg = keyNode.path("alg").asText();
                if ("RSA".equals(kty) && "RS256".equals(alg)) {
                    newCache.put(kid, buildRsaPublicKey(
                            keyNode.path("n").asText(),
                            keyNode.path("e").asText()));
                }
            }
            jwksCache.clear();
            jwksCache.putAll(newCache);
            lastJwksRefresh.set(System.currentTimeMillis());
            log.info("Google JWKS cache refreshed, loaded {} keys", newCache.size());
        } catch (OAuth2Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh Google JWKS cache", e);
            if (jwksCache.isEmpty()) {
                throw invalid("Unable to fetch Google signing keys: " + e.getMessage());
            }
            // else continue with stale cache
        }
    }

    private PublicKey buildRsaPublicKey(String modulusB64, String exponentB64) throws Exception {
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(modulusB64));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(exponentB64));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private boolean isJwksCacheExpired() {
        return System.currentTimeMillis() - lastJwksRefresh.get() > JWKS_CACHE_TTL_MS;
    }

    private static String decodeBase64Url(String input) {
        return new String(Base64.getUrlDecoder().decode(input), StandardCharsets.UTF_8);
    }

    private static OAuth2Exception invalid(String detail) {
        return new OAuth2Exception("Invalid Google ID token: " + detail, "OAUTH2_INVALID_ID_TOKEN");
    }

    /**
     * Standard claims projected from a verified Google ID Token.
     */
    public record GoogleUserInfo(
            String sub,
            String email,
            boolean emailVerified,
            String name,
            String picture,
            String givenName,
            String familyName,
            String locale
    ) {
        static GoogleUserInfo fromClaims(Map<String, Object> claims) {
            Object emailVerifiedObj = claims.get("email_verified");
            boolean emailVerified = emailVerifiedObj instanceof Boolean
                    ? (Boolean) emailVerifiedObj
                    : Boolean.parseBoolean(String.valueOf(emailVerifiedObj));
            return new GoogleUserInfo(
                    (String) claims.get("sub"),
                    (String) claims.get("email"),
                    emailVerified,
                    (String) claims.get("name"),
                    (String) claims.get("picture"),
                    (String) claims.get("given_name"),
                    (String) claims.get("family_name"),
                    (String) claims.get("locale"));
        }
    }
}
