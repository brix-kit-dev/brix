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
package io.brix.platform.auth.oauth2;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 Identity Provider (IdP) Configuration Properties
 * <p>
 * Supports configuring multiple third-party identity providers, including:
 * <ul>
 *   <li>Google OAuth2</li>
 *   <li>WeChat Open Platform</li>
 *   <li>GitHub (optional)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Configuration example (application.yml):
 * <pre>
 * platform:
 *   oauth2:
 *     enabled: true
 *     providers:
 *       google:
 *         enabled: true
 *         client-id: your-google-client-id
 *         client-secret: your-google-client-secret
 *         redirect-uri: http://localhost:8080/api/v1/oauth2/callback/google
 *         scope: openid,profile,email
 *       wechat:
 *         enabled: true
 *         client-id: your-wechat-app-id
 *         client-secret: your-wechat-app-secret
 *         redirect-uri: http://localhost:8080/api/v1/oauth2/callback/wechat
 *         scope: snsapi_login
 * </pre>
 * </p>
 *
 * <p>
 * Note: This class is enabled via {@code @EnableConfigurationProperties}
 * in {@link OAuth2Config}, no need to add {@code @Component} annotation.
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Data
@ConfigurationProperties(prefix = "platform.oauth2")
public class OAuth2Properties {

    /**
     * Whether to enable OAuth2 login
     */
    private boolean enabled = false;

    /**
     * Frontend redirect URL after OAuth2 callback
     * Default: /login/callback
     */
    private String frontendCallbackUrl = "/login/callback";

    /**
     * Default redirect URL after successful login
     */
    private String defaultSuccessUrl = "/";

    /**
     * Redirect URL after login failure
     */
    private String failureUrl = "/login?error=oauth2";

    /**
     * Auto-register new users
     * Whether to automatically create new user when OAuth2 user first logs in without bound account
     */
    private boolean autoRegister = true;

    /**
     * State parameter validity period (seconds)
     * Used to prevent CSRF attacks
     */
    private int stateExpireSeconds = 300;

    /**
     * Identity provider configuration map
     * Key is provider identifier (e.g., google, wechat)
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * Single Identity Provider Configuration
     */
    @Data
    public static class ProviderConfig {
        /**
         * Whether to enable this provider
         */
        private boolean enabled = false;

        /**
         * Provider display name (for frontend display)
         */
        private String displayName;

        /**
         * Provider icon URL or CSS class name
         */
        private String icon;

        /**
         * Client ID (App ID)
         */
        private String clientId;

        /**
         * Client secret (App Secret)
         */
        private String clientSecret;

        /**
         * Authorization endpoint URL
         */
        private String authorizationUri;

        /**
         * Token endpoint URL
         */
        private String tokenUri;

        /**
         * User info endpoint URL
         */
        private String userInfoUri;

        /**
         * Callback URI (Redirect URI)
         */
        private String redirectUri;

        /**
         * Requested scope (comma-separated)
         */
        private String scope;

        /**
         * User ID field name (extracted from user info response)
         */
        private String userIdAttribute = "id";

        /**
         * Username field name
         */
        private String userNameAttribute = "name";

        /**
         * Email field name
         */
        private String emailAttribute = "email";

        /**
         * Avatar field name
         */
        private String avatarAttribute = "avatar";

        /**
         * Whether to use PKCE (Proof Key for Code Exchange)
         */
        private boolean usePkce = false;

        /**
         * Additional request parameters
         */
        private Map<String, String> additionalParams = new HashMap<>();
    }

    /**
     * Get enabled provider configurations
     *
     * @return Map of enabled providers
     */
    public Map<String, ProviderConfig> getEnabledProviders() {
        Map<String, ProviderConfig> enabledProviders = new HashMap<>();
        for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
            if (entry.getValue().isEnabled()) {
                enabledProviders.put(entry.getKey(), entry.getValue());
            }
        }
        return enabledProviders;
    }

    /**
     * Check if specified provider is enabled
     *
     * @param providerId Provider identifier
     * @return Whether enabled
     */
    public boolean isProviderEnabled(String providerId) {
        ProviderConfig config = providers.get(providerId);
        return config != null && config.isEnabled();
    }
}
