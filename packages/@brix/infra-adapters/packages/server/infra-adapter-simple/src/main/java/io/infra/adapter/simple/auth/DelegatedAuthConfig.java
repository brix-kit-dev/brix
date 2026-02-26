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

import java.time.Duration;

/**
 * Delegated Authentication Configuration
 * 
 * <p>Configuration class for {@link io.infra.adapter.simple.DelegatedAuthContextCapability}.
 * Contains OAuth 2.0 Token Introspection endpoint settings.</p>
 * 
 * <h3>Configuration Example (配置示例)</h3>
 * <pre>{@code
 * brix:
 *   capability:
 *     auth-context:
 *       type: delegated
 *       delegated:
 *         token-validation-url: https://customer.com/oauth/introspect
 *         client-id: ${OAUTH_CLIENT_ID}
 *         client-secret: ${OAUTH_CLIENT_SECRET}
 *         cache-ttl: PT5M
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
public class DelegatedAuthConfig {

    /**
     * OAuth 2.0 Token Introspection endpoint URL.
     * 【Token 验证端点】
     */
    private String tokenValidationUrl;

    /**
     * OAuth client ID.
     * 【OAuth 客户端ID】
     */
    private String clientId;

    /**
     * OAuth client secret.
     * 【OAuth 客户端密钥】
     */
    private String clientSecret;

    /**
     * Cache time-to-live duration. Default: 5 minutes.
     * 【缓存有效期】
     */
    private Duration cacheTtl;

    public String getTokenValidationUrl() {
        return tokenValidationUrl;
    }

    public void setTokenValidationUrl(String tokenValidationUrl) {
        this.tokenValidationUrl = tokenValidationUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    @Override
    public String toString() {
        return "DelegatedAuthConfig{" +
                "tokenValidationUrl='" + tokenValidationUrl + '\'' +
                ", clientId='" + clientId + '\'' +
                ", cacheTtl=" + cacheTtl +
                '}';
    }
}
