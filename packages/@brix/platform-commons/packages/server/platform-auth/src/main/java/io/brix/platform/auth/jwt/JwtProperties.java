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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT Configuration Properties
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "platform.security.jwt")
public class JwtProperties {

    /** Whether to enable JWT validation */
    private boolean enabled = true;

    /** RS256 public key path (verification side) */
    private String publicKeyPath = "classpath:keys/public.pem";

    /**
     * RS256 private key path (issuance side, PKCS#8 PEM).
     *
     * <p>Required by {@code JwtIssuerCapability} implementation. Not required for
     * pure validation-only deployments (e.g., gateway / resource server hosts).</p>
     *
     * @since 3.2.0
     */
    private String privateKeyPath = "classpath:keys/private.pem";

    /** JWT issuer */
    private String issuer = "brix-auth-center";

    /** JWT audience */
    private String audience = "brix-platform-api";

    /** Clock skew tolerance time (seconds) */
    private int clockSkewSeconds = 60;

    /**
     * Access Token lifetime in seconds (default 1 hour).
     *
     * @since 3.2.0
     */
    private long accessTokenExpirationSeconds = 3600L;

    /**
     * Identity Token lifetime in seconds (default 5 minutes).
     *
     * <p>Identity Token 是多租户登录第一阶段的过渡令牌，仅允许
     * {@code select-tenant} / {@code register-tenant} 两个动作，
     * 故有效期必须远短于 Access Token。</p>
     *
     * @since 3.2.0
     */
    private long identityTokenExpirationSeconds = 300L;

    /**
     * Token version claim ({@code tv}) for future format migrations.
     *
     * @since 3.2.0
     */
    private int tokenVersion = 2;

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public void setAccessTokenExpirationSeconds(long accessTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public long getIdentityTokenExpirationSeconds() {
        return identityTokenExpirationSeconds;
    }

    public void setIdentityTokenExpirationSeconds(long identityTokenExpirationSeconds) {
        this.identityTokenExpirationSeconds = identityTokenExpirationSeconds;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }
}
