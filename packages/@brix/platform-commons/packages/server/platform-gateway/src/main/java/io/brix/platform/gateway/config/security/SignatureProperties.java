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
package io.brix.platform.gateway.config.security;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Configuration properties for HMAC-SHA256 request signature verification.
 *
 * <p>Binds to the {@code gateway.signature} namespace in application configuration.
 * Provides settings for enabling/disabling signature verification, configuring the
 * secret key, timestamp tolerance, protected paths, and header names.</p>
 *
 * <h3>Production Security Enforcement</h3>
 * <p>At startup, a {@link PostConstruct} validation ensures that the default
 * secret key is <b>never</b> used in production. If the {@code prod} Spring profile
 * is active and the secret key has not been overridden, the application will
 * <b>fail fast</b> with a clear error message.</p>
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * gateway:
 *   signature:
 *     enabled: true
 *     secret-key: ${GATEWAY_SIGNATURE_SECRET}
 *     timestamp-tolerance-seconds: 300
 *     protected-paths:
 *       - /open-api/**
 * </pre>
 *
 * <h3>Environment Variable</h3>
 * <p>In production deployments, set the secret key via the
 * {@code GATEWAY_SIGNATURE_SECRET} environment variable or a secrets manager.
 * Never commit real secrets to source control.</p>
 *
 * @author Brix Platform Team
 * @version 1.1.0
 * @since 2025-12-13
 */
@Component
@ConfigurationProperties(prefix = "gateway.signature")
public class SignatureProperties {

    private static final Logger log = LoggerFactory.getLogger(SignatureProperties.class);

    /**
     * The well-known default secret key shipped with the source code.
     * Used <b>only</b> as a sentinel value to detect misconfigured production deployments.
     */
    private static final String INSECURE_DEFAULT_KEY = "brix-default-signature-key-change-in-production";

    /**
     * Injected Spring {@link Environment} used to detect active profiles during
     * the {@link #validateProductionSecretKey()} post-construct check.
     */
    private final Environment environment;

    /**
     * Creates a new {@code SignatureProperties} instance.
     *
     * @param environment the Spring environment, used for profile detection
     */
    public SignatureProperties(Environment environment) {
        this.environment = environment;
    }

    /**
     * Whether signature verification is enabled.
     */
    private boolean enabled = true;

    /**
     * HMAC-SHA256 signature secret key.
     *
     * <p><b>MUST</b> be overridden in production via the {@code GATEWAY_SIGNATURE_SECRET}
     * environment variable. The default value is intentionally insecure to ensure it
     * is never accidentally used outside of local development.</p>
     */
    private String secretKey = INSECURE_DEFAULT_KEY;

    /**
     * Timestamp tolerance window in seconds.
     * Requests with a timestamp older than this value are rejected as potential replay attacks.
     */
    private int timestampToleranceSeconds = 300;

    /**
     * URL path patterns that require signature verification.
     */
    private List<String> protectedPaths = new ArrayList<>(List.of("/open-api/**"));

    /**
     * HTTP header name carrying the request signature.
     */
    private String signatureHeader = "X-Signature";

    /**
     * HTTP header name carrying the request timestamp.
     */
    private String timestampHeader = "X-Timestamp";

    /**
     * HTTP header name carrying the request nonce (anti-replay).
     */
    private String nonceHeader = "X-Nonce";

    /**
     * Signature algorithm identifier (default: HmacSHA256).
     */
    private String algorithm = "HmacSHA256";

    // ==================== Production Startup Validation ====================

    /**
     * Validates that the secret key has been properly configured for production.
     *
     * <p>This check runs automatically after dependency injection. If the {@code prod}
     * Spring profile is active and the secret key still equals the insecure default,
     * the application will throw an {@link IllegalStateException} and refuse to start.</p>
     *
     * <p>In non-production profiles a warning is logged instead, allowing local
     * development to proceed without requiring a real secret.</p>
     *
     * @throws IllegalStateException if the default key is detected under the {@code prod} profile
     */
    @PostConstruct
    void validateProductionSecretKey() {
        if (!enabled) {
            log.info("Gateway signature verification is disabled; skipping secret key validation.");
            return;
        }

        boolean isProduction = environment.matchesProfiles("prod", "production");

        if (INSECURE_DEFAULT_KEY.equals(secretKey)) {
            if (isProduction) {
                throw new IllegalStateException(
                    "FATAL: gateway.signature.secret-key is still using the insecure default value. "
                    + "In production you MUST set 'GATEWAY_SIGNATURE_SECRET' environment variable "
                    + "or configure 'gateway.signature.secret-key' in your deployment. "
                    + "Generate a key with: openssl rand -base64 32"
                );
            } else {
                log.warn("=========================================================================");
                log.warn("  WARNING: gateway.signature.secret-key is using the default value.");
                log.warn("  This is acceptable for local development but MUST be changed before");
                log.warn("  deploying to production. Set the GATEWAY_SIGNATURE_SECRET env variable.");
                log.warn("=========================================================================");
            }
        }

        // Additional validation: ensure the key has reasonable entropy (at least 32 chars)
        if (secretKey != null && secretKey.length() < 32) {
            String msg = "gateway.signature.secret-key is shorter than 32 characters. "
                + "Use a strong key: openssl rand -base64 32";
            if (isProduction) {
                throw new IllegalStateException("FATAL: " + msg);
            } else {
                log.warn(msg);
            }
        }
    }

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getTimestampToleranceSeconds() {
        return timestampToleranceSeconds;
    }

    public void setTimestampToleranceSeconds(int timestampToleranceSeconds) {
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getTimestampHeader() {
        return timestampHeader;
    }

    public void setTimestampHeader(String timestampHeader) {
        this.timestampHeader = timestampHeader;
    }

    public String getNonceHeader() {
        return nonceHeader;
    }

    public void setNonceHeader(String nonceHeader) {
        this.nonceHeader = nonceHeader;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
