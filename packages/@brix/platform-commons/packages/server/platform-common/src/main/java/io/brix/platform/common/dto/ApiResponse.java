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
package io.brix.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import io.brix.platform.common.exception.PlatformErrorCode;

/**
 * Standard REST API Response - Standardization v1.0
 * <p>
 * Unified response format ensuring consistent frontend-backend communication
 * </p>
 * 
 * <h3>Success Response Format</h3>
 * <pre>{@code
 * {
 *   "success": true,
 *   "code": "OK",
 *   "message": "Operation successful",
 *   "data": { ... },
 *   "timestamp": "2026-01-04T10:00:00Z",
 *   "traceId": "abc123"
 * }
 * }</pre>
 * 
 * <h3>Error Response Format</h3>
 * <pre>{@code
 * {
 *   "success": false,
 *   "code": "AUTH-A-002",
 *   "message": "Token has expired",
 *   "details": { "field": "token", "reason": "expired" },
 *   "timestamp": "2026-01-04T10:00:00Z",
 *   "traceId": "abc123"
 * }
 * }</pre>
 *
 * @param <T> Actual business data type
 * @author Brix Platform Authors Platform Team
 * @version 2.0.0 (Standardization v1.0)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "code", "message", "data", "details", "timestamp", "traceId"})
public final class ApiResponse<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = -4896843920452187231L;

    /** Whether the operation was successful */
    private final boolean success;
    
    /** Error code */
    private final String code;
    
    /** Message description */
    private final String message;
    
    /** Business data payload */
    private final T data;
    
    /** Error details (optional, for debugging) */
    private final Map<String, Object> details;
    
    /** Response timestamp */
    private final Instant timestamp;
    
    /** Trace ID for distributed tracing */
    private final String traceId;

    private ApiResponse(boolean success, String code, String message, T data, 
                       Map<String, Object> details, String traceId) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.details = details;
        this.timestamp = Instant.now();
        this.traceId = traceId;
    }

    // ========== Success Response Builder Methods ==========

    /**
     * Build a success response with data
     *
     * @param data Business data payload
     * @return Success response
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, PlatformErrorCode.SUCCESS.getCode(), 
                PlatformErrorCode.SUCCESS.getMessage(), data, null, null);
    }

    /**
     * Build a success response with data and trace ID
     *
     * @param data    Business data payload
     * @param traceId Trace ID for distributed tracing
     * @return Success response
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(true, PlatformErrorCode.SUCCESS.getCode(),
                PlatformErrorCode.SUCCESS.getMessage(), data, null, traceId);
    }

    /**
     * Build a success response without data
     *
     * @return Success response
     */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    // ========== Failure Response Builder Methods ==========

    /**
     * Build a failure response with the specified error code
     *
     * @param errorCode Platform error code
     * @return Failure response
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), 
                null, null, null);
    }

    /**
     * Build a failure response with custom message
     *
     * @param errorCode Platform error code
     * @param message   Custom error description
     * @return Failure response
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, null, null);
    }

    /**
     * Build a failure response with error code and trace ID
     *
     * @param errorCode Platform error code
     * @param traceId   Trace ID for distributed tracing
     * @return Failure response
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode, String message, String traceId) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, null, traceId);
    }

    /**
     * Build a failure response with details
     *
     * @param errorCode Platform error code
     * @param message   Custom error description
     * @param details   Error details
     * @param traceId   Trace ID for distributed tracing
     * @return Failure response
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode, String message, 
                                           Map<String, Object> details, String traceId) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, details, traceId);
    }

    /**
     * Build a failure response with custom error code (for plugin extension error codes)
     *
     * @param code    Custom error code
     * @param message Error description
     * @return Failure response
     */
    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null, null, null);
    }

    // ========== Getters ==========

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiResponse<?> that = (ApiResponse<?>) o;
        return success == that.success && 
               Objects.equals(code, that.code) && 
               Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, code, data);
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "success=" + success +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
