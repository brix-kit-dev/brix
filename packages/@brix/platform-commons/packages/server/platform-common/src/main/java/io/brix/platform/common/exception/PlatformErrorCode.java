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
package io.brix.platform.common.exception;

import java.util.Arrays;
import java.util.Optional;

/**
 * Platform-Level Error Code Definitions - Standard v1.0
 * <p>
 * Unified error code specification for frontend-backend collaboration and auditing
 * </p>
 *
 * <h3>Error Code Format Specification</h3>
 * <pre>
 * Format: {Module}-{Category}-{Sequence}
 *
 * Modules:
 *   PLATFORM  - Platform Commons
 *   AUTH      - Authentication Center
 *   GATEWAY   - Gateway
 *   ENGINE    - Plugin Engine
 *   PLUGIN    - Plugin Prefix (e.g., PLUGIN-USER)
 *
 * Categories:
 *   A - Authentication/Authorization (4xx)
 *   B - Business Logic (4xx)
 *   S - System Error (5xx)
 *   V - Parameter Validation (4xx)
 * </pre>
 * 
 * <h3>Unified Response Format</h3>
 * <pre>
 * // Success response
 * {
 *   "success": true,
 *   "code": "OK",
 *   "data": { ... },
 *   "timestamp": "2026-01-04T10:00:00Z",
 *   "traceId": "abc123"
 * }
 *
 * // Error response
 * {
 *   "success": false,
 *   "code": "AUTH-A-002",
 *   "message": "Token has expired",
 *   "details": { ... },
 *   "timestamp": "2026-01-04T10:00:00Z",
 *   "traceId": "abc123"
 * }
 * </pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 2.0.0 (Standardization v1.0)
 */
public enum PlatformErrorCode {
    
    // ========== Common Success ==========
    SUCCESS("OK", "Operation successful", 200),
    
    // ========== PLATFORM Common Errors (PLATFORM-*-*) ==========
    INVALID_PARAMETER("PLATFORM-V-001", "Invalid request parameter", 400),
    RESOURCE_NOT_FOUND("PLATFORM-B-001", "Target resource not found", 404),
    DUPLICATED_OPERATION("PLATFORM-B-002", "Duplicate operation", 409),
    INTERNAL_ERROR("PLATFORM-S-001", "Internal system error", 500),
    REMOTE_SERVICE_ERROR("PLATFORM-S-002", "Downstream service call failed", 502),
    DATA_INCONSISTENCY("PLATFORM-S-003", "Data consistency validation failed", 500),
    CONCURRENT_MODIFICATION("PLATFORM-B-003", "Data has been modified by another user, please refresh and retry", 409),
    
    // ========== AUTH Authentication/Authorization Errors (AUTH-A-*) ==========
    UNAUTHORIZED("AUTH-A-001", "Unauthorized", 401),
    AUTH_FAILED("AUTH-A-002", "Authentication failed", 401),
    TOKEN_EXPIRED("AUTH-A-003", "Token has expired", 401),
    TOKEN_INVALID("AUTH-A-004", "Token is invalid", 401),
    TOKEN_REVOKED("AUTH-A-005", "Token has been revoked", 401),
    FORBIDDEN("AUTH-A-006", "Access forbidden", 403),
    ACCOUNT_LOCKED("AUTH-A-007", "Account has been locked", 403),
    ACCOUNT_DISABLED("AUTH-A-008", "Account has been disabled", 403),
    REFRESH_TOKEN_EXPIRED("AUTH-A-009", "Refresh token has expired", 401),
    OAUTH_ERROR("AUTH-A-010", "OAuth authentication failed", 401),
    
    // ========== ENGINE Plugin Engine Errors (ENGINE-B-*) ==========
    PLUGIN_NOT_FOUND("ENGINE-B-001", "Plugin not registered", 404),
    PLUGIN_ALREADY_REGISTERED("ENGINE-B-002", "Plugin already registered", 409),
    PLUGIN_NOT_TRUSTED("ENGINE-A-001", "Plugin not authorized", 403),
    PLUGIN_CREDENTIAL_INVALID("ENGINE-A-002", "Plugin credential invalid", 401),
    ROUTE_QUOTA_EXCEEDED("ENGINE-B-003", "Route quota exceeded", 429),
    GLOBAL_ROUTE_QUOTA_EXCEEDED("ENGINE-B-004", "Global route quota exceeded, maximum 500 routes allowed", 429),
    PLUGIN_ROUTE_QUOTA_EXCEEDED("ENGINE-B-005", "Plugin route quota exceeded, maximum 50 routes per plugin", 429),
    MENU_QUOTA_EXCEEDED("ENGINE-B-006", "Menu quota exceeded", 429),
    MENU_DEPTH_EXCEEDED("ENGINE-B-007", "Menu depth exceeded, maximum 3 levels allowed", 429),
    PLUGIN_MENU_QUOTA_EXCEEDED("ENGINE-B-008", "Plugin menu quota exceeded, maximum 30 menus per plugin", 429),
    GLOBAL_MENU_QUOTA_EXCEEDED("ENGINE-B-009", "Global menu quota exceeded", 429),
    
    // ========== GATEWAY Gateway Errors (GATEWAY-*-*) ==========
    ROUTE_NOT_FOUND("GATEWAY-B-001", "Route not found", 404),
    RATE_LIMIT_EXCEEDED("GATEWAY-B-002", "Request rate limit exceeded", 429),
    CIRCUIT_BREAKER_OPEN("GATEWAY-S-001", "Service circuit breaker open", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    PlatformErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Find enum by code for recovering error semantics from logs or external input
     *
     * @param code Error code
     * @return Matching error code enum
     */
    public static Optional<PlatformErrorCode> fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
    
    /**
     * Check if this is a success code
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
    
    /**
     * Check if this is a client error (4xx)
     */
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }
    
    /**
     * Check if this is a server error (5xx)
     */
    public boolean isServerError() {
        return httpStatus >= 500;
    }
}
