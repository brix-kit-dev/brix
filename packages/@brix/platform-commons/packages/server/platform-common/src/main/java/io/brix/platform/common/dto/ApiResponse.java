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
 * 标准 REST 返回- 标准v1.0
 * <p>
 * 统一响应格式，保证前后端交互口径一致
 * </p>
 * 
 * <h3>成功响应格式</h3>
 * <pre>{@code
 * {
 *   "success": true,
 *   "code": "OK",
 *   "message": "操作成功",
 *   "data": { ... },
 *   "timestamp": "2026-01-04T10:00:00Z",
 *   "traceId": "abc123"
 * }
 * }</pre>
 * 
 * <h3>错误响应格式</h3>
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
 * @param <T> 真实业务数据类型
 * @author Brix Platform Authors Platform Team
 * @version 2.0.0 (Standardization v1.0)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "code", "message", "data", "details", "timestamp", "traceId"})
public final class ApiResponse<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = -4896843920452187231L;

    /** 是否成功 */
    private final boolean success;
    
    /** 错误*/
    private final String code;
    
    /** 消息描述 */
    private final String message;
    
    /** 业务数据 */
    private final T data;
    
    /** 错误详情（可选，用于调试*/
    private final Map<String, Object> details;
    
    /** 响应时间*/
    private final Instant timestamp;
    
    /** 杩借釜ID */
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

    // ========== 成功响应构建方法 ==========

    /**
     * 构建成功响应（带数据
     *
     * @param data 业务数据载荷
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, PlatformErrorCode.SUCCESS.getCode(), 
                PlatformErrorCode.SUCCESS.getMessage(), data, null, null);
    }

    /**
     * 构建成功响应（带数据和追踪ID
     *
     * @param data    业务数据载荷
     * @param traceId 杩借釜ID
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(true, PlatformErrorCode.SUCCESS.getCode(),
                PlatformErrorCode.SUCCESS.getMessage(), data, null, traceId);
    }

    /**
     * 构建无数据的成功响应
     *
     * @return 成功响应
     */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    // ========== 失败响应构建方法 ==========

    /**
     * 根据指定错误码构建失败响
     *
     * @param errorCode 平台错误
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), 
                null, null, null);
    }

    /**
     * 根据自定义消息构建失败响
     *
     * @param errorCode 平台错误
     * @param message   自定义错误描
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, null, null);
    }

    /**
     * 根据错误码和追踪ID构建失败响应
     *
     * @param errorCode 平台错误
     * @param traceId   杩借釜ID
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode, String message, String traceId) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, null, traceId);
    }

    /**
     * 构建带详情的失败响应
     *
     * @param errorCode 平台错误
     * @param message   自定义错误描
     * @param details   閿欒璇︽儏
     * @param traceId   杩借釜ID
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(PlatformErrorCode errorCode, String message, 
                                           Map<String, Object> details, String traceId) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, details, traceId);
    }

    /**
     * 使用自定义错误码构建失败响应（用于插件扩展错误码
     *
     * @param code    自定义错误码
     * @param message 閿欒鎻忚堪
     * @return 失败响应
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
