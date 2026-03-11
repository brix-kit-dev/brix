/*
 * Copyright 2024-2026 Brix Platform Authors. All rights reserved.
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
package io.brix.platform.auth.exception;

/**
 * Exception thrown when PKCE (Proof Key for Code Exchange) operations fail.
 *
 * <p>This exception covers failures in the PKCE flow for OAuth2 authentication,
 * including code verifier generation and code challenge computation.</p>
 *
 * <h3>Design Rationale</h3>
 * <p>Following the Fault Isolation architecture principle,
 * cryptographic operation failures must be wrapped in domain-specific exceptions
 * rather than generic RuntimeException. This enables:
 * <ul>
 *   <li>Clear distinction between PKCE errors and other OAuth2 errors</li>
 *   <li>Appropriate security logging (without exposing sensitive data)</li>
 *   <li>Fallback strategy selection in upper layers</li>
 * </ul>
 *
 * <h3>PKCE Flow Context</h3>
 * <p>PKCE is a security extension for OAuth2 public clients. The flow:
 * <ol>
 *   <li>Client generates random code_verifier (high entropy string)</li>
 *   <li>Client computes code_challenge = SHA256(code_verifier)</li>
 *   <li>Client sends code_challenge with authorization request</li>
 *   <li>Server verifies code_verifier matches code_challenge during token exchange</li>
 * </ol>
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 * @see io.brix.platform.auth.oauth2.OAuth2UserService
 */
public class PkceGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for categorization in monitoring systems.
     */
    private final String errorCode;

    /**
     * The PKCE operation that failed (e.g., "code_challenge_generation").
     */
    private final String operation;

    /**
     * Constructs a new PkceGenerationException with the specified message and cause.
     *
     * @param message the detail message describing the PKCE generation error
     * @param cause   the underlying cause (typically NoSuchAlgorithmException)
     */
    public PkceGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUTH_PKCE_ERROR";
        this.operation = "code_challenge_generation";
    }

    /**
     * Constructs a new PkceGenerationException with full details.
     *
     * @param message   the detail message describing the PKCE generation error
     * @param operation the PKCE operation that failed
     * @param cause     the underlying cause
     */
    public PkceGenerationException(String message, String operation, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUTH_PKCE_ERROR";
        this.operation = operation;
    }

    /**
     * Returns the error code for this exception.
     *
     * @return the error code, e.g., "AUTH_PKCE_ERROR"
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the PKCE operation that failed.
     *
     * @return the operation name, e.g., "code_challenge_generation"
     */
    public String getOperation() {
        return operation;
    }
}
