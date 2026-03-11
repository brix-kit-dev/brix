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
 * OAuth2 Authentication Exception
 * <p>
 * Used for various exception scenarios in OAuth2 login flow:
 * <ul>
 *   <li>Invalid state parameter</li>
 *   <li>Token exchange failure</li>
 *   <li>User info retrieval failure</li>
 *   <li>User binding failure</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
public class OAuth2Exception extends RuntimeException {

    /**
     * Error code
     */
    private final String errorCode;

    /**
     * Create OAuth2 exception
     *
     * @param message Error message
     */
    public OAuth2Exception(String message) {
        super(message);
        this.errorCode = "OAUTH2_ERROR";
    }

    /**
     * Create OAuth2 exception
     *
     * @param message   Error message
     * @param errorCode Error code
     */
    public OAuth2Exception(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create OAuth2 exception
     *
     * @param message Error message
     * @param cause   Original exception
     */
    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "OAUTH2_ERROR";
    }

    /**
     * Get error code
     *
     * @return Error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Invalid state parameter
     */
    public static OAuth2Exception invalidState() {
        return new OAuth2Exception("Invalid state parameter, possible CSRF attack", "INVALID_STATE");
    }

    /**
     * Token exchange failed
     */
    public static OAuth2Exception tokenExchangeFailed(String reason) {
        return new OAuth2Exception("Token exchange failed: " + reason, "TOKEN_EXCHANGE_FAILED");
    }

    /**
     * User info retrieval failed
     */
    public static OAuth2Exception userInfoFailed(String reason) {
        return new OAuth2Exception("Failed to get user info: " + reason, "USER_INFO_FAILED");
    }

    /**
     * Provider not enabled
     */
    public static OAuth2Exception providerNotEnabled(String provider) {
        return new OAuth2Exception("Unsupported login method: " + provider, "PROVIDER_NOT_ENABLED");
    }

    /**
     * User binding failed
     */
    public static OAuth2Exception bindingFailed(String reason) {
        return new OAuth2Exception("User binding failed: " + reason, "BINDING_FAILED");
    }
}
