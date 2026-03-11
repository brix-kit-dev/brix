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

/**
 * OAuth2 User Info
 * <p>
 * Standardized user info retrieved from third-party identity providers.
 * Different providers' raw fields are mapped to a unified field structure.
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Data
public class OAuth2UserInfo {

    /**
     * Identity provider identifier (google, wechat, github)
     */
    private String provider;

    /**
     * User's unique identifier at the provider
     * <ul>
     *   <li>Google: sub</li>
     *   <li>WeChat: openid</li>
     *   <li>GitHub: id</li>
     * </ul>
     */
    private String providerId;

    /**
     * User display name
     */
    private String name;

    /**
     * User email (may be empty)
     */
    private String email;

    /**
     * User avatar URL (may be empty)
     */
    private String avatar;

    /**
     * Raw attributes JSON string
     * Preserves complete user info returned by provider for extension
     */
    private String rawAttributes;

    /**
     * Generate unique OAuth2 binding identifier
     * Format: provider:providerId
     *
     * @return Binding identifier
     */
    public String getBindingKey() {
        return provider + ":" + providerId;
    }
}
