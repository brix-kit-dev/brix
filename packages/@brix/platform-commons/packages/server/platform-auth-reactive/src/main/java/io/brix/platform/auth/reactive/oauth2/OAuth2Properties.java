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
package io.brix.platform.auth.reactive.oauth2;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * OAuth2 Identity Provider (IdP) configuration properties.
 *
 * <p>Supports configuring multiple third-party identity providers, including:
 * <ul>
 *   <li>Google OAuth2</li>
 *   <li>WeChat Open Platform</li>
 *   <li>GitHub OAuth</li>
 * </ul>
 *
 * <h3>Configuration Example (application.yml)</h3>
 * <pre>{@code
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
 * }</pre>
 *
 * <h3>Architecture Note</h3>
 * <p>This class is enabled via {@code @EnableConfigurationProperties} in
 * {@link OAuth2Config}. It resides in platform-auth-reactive because all
 * OAuth2 endpoints are currently reactive (WebFlux + WebClient).
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Data
@ConfigurationProperties(prefix = "platform.oauth2")
public class OAuth2Properties {

    /** Whether to enable OAuth2 login globally. */
    private boolean enabled = false;

    /**
     * Frontend redirect URL after OAuth2 callback completes.
     * Default: {@code /login/callback}
     */
    private String frontendCallbackUrl = "/login/callback";

    /** Default redirect URL after successful login. */
    private String defaultSuccessUrl = "/";

    /** Redirect URL after login failure. */
    private String failureUrl = "/login?error=oauth2";

    /**
     * Whether to automatically create a new user when an OAuth2 user first
     * logs in without a bound account.
     */
    private boolean autoRegister = true;

    /**
     * State parameter validity period in seconds.
     * Used to prevent CSRF attacks during the OAuth2 flow.
     */
    private int stateExpireSeconds = 300;

    /**
     * Identity provider configuration map.
     * Key is the provider identifier (e.g., "google", "wechat", "github").
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * Configuration for a single Identity Provider.
     */
    @Data
    public static class ProviderConfig {

        /** Whether this provider is enabled. */
        private boolean enabled = false;

        /** Provider display name (shown on the login page). */
        private String displayName;

        /** Provider icon URL or CSS class name. */
        private String icon;

        /** Client ID (App ID) obtained from the IdP console. */
        private String clientId;

        /** Client secret (App Secret) obtained from the IdP console. */
        private String clientSecret;

        /** Authorization endpoint URL. */
        private String authorizationUri;

        /** Token endpoint URL. */
        private String tokenUri;

        /** User info endpoint URL. */
        private String userInfoUri;

        /** OAuth2 callback redirect URI. */
        private String redirectUri;

        /** Requested scope (comma-separated, converted to space-separated for Google). */
        private String scope;

        /** Field name for extracting the user's unique ID from the user info response. */
        private String userIdAttribute = "id";

        /** Field name for extracting the username. */
        private String userNameAttribute = "name";

        /** Field name for extracting the email. */
        private String emailAttribute = "email";

        /** Field name for extracting the avatar URL. */
        private String avatarAttribute = "avatar";

        /** Whether to use PKCE (Proof Key for Code Exchange) for this provider. */
        private boolean usePkce = false;

        /** Additional request parameters to append to the authorization URL. */
        private Map<String, String> additionalParams = new HashMap<>();
    }

    /**
     * Returns a filtered map containing only enabled provider configurations.
     *
     * @return map of enabled providers (key = provider id, value = config)
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
     * Checks whether a specific provider is enabled.
     *
     * @param providerId provider identifier
     * @return {@code true} if the provider exists and is enabled
     */
    public boolean isProviderEnabled(String providerId) {
        ProviderConfig config = providers.get(providerId);
        return config != null && config.isEnabled();
    }
}
