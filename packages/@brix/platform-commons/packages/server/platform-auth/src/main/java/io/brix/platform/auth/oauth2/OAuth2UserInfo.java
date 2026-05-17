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

/**
 * Standardized OAuth2 user information retrieved from a third-party Identity Provider.
 *
 * <p>Maps provider-specific raw fields to a unified structure:
 * <table>
 *   <tr><th>Provider</th><th>userId field</th><th>name field</th></tr>
 *   <tr><td>Google</td><td>sub</td><td>name</td></tr>
 *   <tr><td>GitHub</td><td>id</td><td>login / name</td></tr>
 *   <tr><td>WeChat</td><td>openid</td><td>nickname</td></tr>
 * </table>
 *
 * <h3>Architecture Note</h3>
 * <p>This class resides in platform-auth core because it is a shared domain type
 * consumed by both the reactive OAuth2 flow (platform-auth-reactive) and any
 * downstream service that processes OAuth2 user data.
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
public class OAuth2UserInfo {

    /** Identity provider identifier (e.g., "google", "wechat", "github"). */
    private String provider;

    /** User's unique identifier at the provider (e.g., Google sub, WeChat openid). */
    private String providerId;

    /** User display name. */
    private String name;

    /** User email address (may be null for some providers). */
    private String email;

    /** User avatar URL (may be null). */
    private String avatar;

    /** Raw JSON attributes string for extensibility. */
    private String rawAttributes;

    // ========== Getters & Setters ==========

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getRawAttributes() {
        return rawAttributes;
    }

    public void setRawAttributes(String rawAttributes) {
        this.rawAttributes = rawAttributes;
    }

    /**
     * Generates a unique OAuth2 binding key in the format {@code provider:providerId}.
     *
     * <p>This key is used to link OAuth2 identities to platform user accounts,
     * enabling multi-provider login for a single user.
     *
     * @return the binding key, e.g., "google:1234567890"
     */
    public String getBindingKey() {
        return provider + ":" + providerId;
    }
}
