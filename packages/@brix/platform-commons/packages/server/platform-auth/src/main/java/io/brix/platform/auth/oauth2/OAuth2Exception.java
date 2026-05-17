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
 * OAuth2 Authentication Exception.
 *
 * <p>Domain-specific exception for all OAuth2 login flow error scenarios:
 * <ul>
 *   <li>Invalid or expired state parameter (CSRF protection)</li>
 *   <li>Token exchange failure with the Identity Provider</li>
 *   <li>User info retrieval failure</li>
 *   <li>Unsupported or disabled provider</li>
 * </ul>
 *
 * <p>Each instance carries an {@code errorCode} for structured error responses
 * and observability integration (logging, monitoring dashboards).
 *
 * <h3>Architecture Note</h3>
 * <p>This class lives in the platform-auth core module because it is referenced
 * by both the reactive OAuth2 controllers (platform-auth-reactive) and the shared
 * domain model. Following the Fault Isolation principle, all OAuth2 errors are
 * wrapped in this domain-specific exception rather than generic RuntimeException.
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
public class OAuth2Exception extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Structured error code for categorization (e.g., "OAUTH2_ERROR", "INVALID_STATE").
     */
    private final String errorCode;

    /**
     * Creates an OAuth2Exception with a default error code.
     *
     * @param message human-readable error description
     */
    public OAuth2Exception(String message) {
        super(message);
        this.errorCode = "OAUTH2_ERROR";
    }

    /**
     * Creates an OAuth2Exception with a specific error code.
     *
     * @param message   human-readable error description
     * @param errorCode structured error code for API responses
     */
    public OAuth2Exception(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates an OAuth2Exception wrapping an underlying cause.
     *
     * @param message human-readable error description
     * @param cause   the root cause of the OAuth2 failure
     */
    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "OAUTH2_ERROR";
    }

    /**
     * Returns the structured error code.
     *
     * @return the error code, e.g., "OAUTH2_ERROR"
     */
    public String getErrorCode() {
        return errorCode;
    }
}
