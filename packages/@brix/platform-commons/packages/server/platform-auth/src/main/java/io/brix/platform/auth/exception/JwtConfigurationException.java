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
 * Exception thrown when JWT configuration is invalid or cannot be loaded.
 *
 * <p>This exception is thrown during application startup when critical JWT configuration
 * (such as public/private keys) cannot be initialized. It replaces generic RuntimeException
 * to provide clearer semantics and better error handling.</p>
 *
 * <h3>Design Rationale</h3>
 * <p>Following the Fault Isolation architecture principle,
 * all exceptions must be domain-specific rather than generic RuntimeException.
 * This enables:
 * <ul>
 *   <li>Proper exception handling in upper layers</li>
 *   <li>Clear error categorization for observability</li>
 *   <li>Structured logging with error codes</li>
 * </ul>
 *
 * <h3>Usage Scenarios</h3>
 * <ul>
 *   <li>JWT public key file not found or unreadable</li>
 *   <li>Invalid key format (not a valid PEM/X509 public key)</li>
 *   <li>Unsupported algorithm (e.g., RSA not available)</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 * @see io.brix.platform.auth.jwt.JwtValidator
 */
public class JwtConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for categorization in monitoring systems.
     */
    private final String errorCode;

    /**
     * The resource path that caused the configuration error.
     */
    private final String resourcePath;

    /**
     * Constructs a new JwtConfigurationException with the specified message and cause.
     *
     * @param message the detail message describing the configuration error
     * @param cause   the underlying cause of this exception
     */
    public JwtConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUTH_JWT_CONFIG_ERROR";
        this.resourcePath = null;
    }

    /**
     * Constructs a new JwtConfigurationException with message, resource path, and cause.
     *
     * @param message      the detail message describing the configuration error
     * @param resourcePath the path to the resource that could not be loaded
     * @param cause        the underlying cause of this exception
     */
    public JwtConfigurationException(String message, String resourcePath, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUTH_JWT_CONFIG_ERROR";
        this.resourcePath = resourcePath;
    }

    /**
     * Constructs a new JwtConfigurationException with full details.
     *
     * @param message      the detail message describing the configuration error
     * @param errorCode    the error code for categorization
     * @param resourcePath the path to the resource that could not be loaded
     * @param cause        the underlying cause of this exception
     */
    public JwtConfigurationException(String message, String errorCode, String resourcePath, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.resourcePath = resourcePath;
    }

    /**
     * Returns the error code for this exception.
     *
     * @return the error code, e.g., "AUTH_JWT_CONFIG_ERROR"
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the resource path that caused the configuration error.
     *
     * @return the resource path, or null if not applicable
     */
    public String getResourcePath() {
        return resourcePath;
    }
}
