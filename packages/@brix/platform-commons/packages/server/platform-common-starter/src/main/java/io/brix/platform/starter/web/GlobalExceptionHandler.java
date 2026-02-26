package io.brix.platform.starter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import io.brix.platform.common.dto.ApiResponse;
import io.brix.platform.common.exception.PlatformErrorCode;
import io.brix.platform.common.exception.PlatformException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * v2.1 全局异常处理
 * 
 * <p>统一处理所有控制器抛出的异常，返回标准 ApiResponse 格式</p>
 * 
 * <p>处理的异常类型：</p>
 * <ul>
 *   <li>PlatformException - 平台业务异常</li>
 *   <li>MethodArgumentNotValidException - 参数校验异常</li>
 *   <li>BindException - 参数绑定异常</li>
 *   <li>MissingServletRequestParameterException - 缺少请求参数</li>
 *   <li>MethodArgumentTypeMismatchException - 参数类型不匹</li>
 *   <li>HttpRequestMethodNotSupportedException - HTTP 方法不支</li>
 *   <li>HttpMediaTypeNotSupportedException - 媒体类型不支</li>
 *   <li>NoHandlerFoundException - 资源未找</li>
 *   <li>Exception - 兜底处理未知异常</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理平台业务异常
     * 
     * @param ex 平台异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformException(
            PlatformException ex, HttpServletRequest request) {
        
        log.warn("[GlobalExceptionHandler] 业务异常: {} - {}, path: {}", 
            ex.getErrorCode().getCode(), ex.getMessage(), request.getRequestURI());
        
        // 根据错误码确HTTP 状态码
        HttpStatus status = HttpStatus.valueOf(ex.getErrorCode().getHttpStatus());
        
        ApiResponse<Void> response = ApiResponse.failure(
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * 处理参数校验异常（@Valid 注解触发
     * 
     * @param ex 参数校验异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        // 收集所有校验错
        Map<String, String> errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "校验失败",
                (existing, replacement) -> existing
            ));
        
        log.warn("[GlobalExceptionHandler] 参数校验失败: {}, path: {}", 
            errors, request.getRequestURI());
        
        // 将错误信息拼接为消息
        String errorMessage = "参数校验失败: " + errors.entrySet().stream()
            .map(e -> e.getKey() + " - " + e.getValue())
            .collect(Collectors.joining("; "));
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INVALID_PARAMETER,
            errorMessage
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 处理参数绑定异常（表单提交）
     * 
     * @param ex 绑定异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException ex, HttpServletRequest request) {
        
        Map<String, String> errors = ex.getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "绑定失败",
                (existing, replacement) -> existing
            ));
        
        log.warn("[GlobalExceptionHandler] 参数绑定失败: {}, path: {}", 
            errors, request.getRequestURI());
        
        // 将错误信息拼接为消息
        String errorMessage = "参数绑定失败: " + errors.entrySet().stream()
            .map(e -> e.getKey() + " - " + e.getValue())
            .collect(Collectors.joining("; "));
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INVALID_PARAMETER,
            errorMessage
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 处理缺少请求参数异常
     * 
     * @param ex 缺少参数异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        
        String message = String.format("缺少必需参数: %s (类型: %s)", 
            ex.getParameterName(), ex.getParameterType());
        
        log.warn("[GlobalExceptionHandler] {}, path: {}", message, request.getRequestURI());
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INVALID_PARAMETER,
            message
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 处理参数类型不匹配异
     * 
     * @param ex 类型不匹配异
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String message = String.format("参数类型不匹 %s (期望类型: %s)", 
            ex.getName(), 
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        log.warn("[GlobalExceptionHandler] {}, path: {}", message, request.getRequestURI());
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INVALID_PARAMETER,
            message
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 处理 HTTP 方法不支持异
     * 
     * @param ex 方法不支持异
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        
        String message = String.format("HTTP 方法不支 %s", ex.getMethod());
        
        log.warn("[GlobalExceptionHandler] {}, path: {}", message, request.getRequestURI());
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INTERNAL_ERROR,
            message
        );
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }
    
    /**
     * 处理媒体类型不支持异
     * 
     * @param ex 媒体类型不支持异
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        
        String message = String.format("媒体类型不支 %s", ex.getContentType());
        
        log.warn("[GlobalExceptionHandler] {}, path: {}", message, request.getRequestURI());
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INTERNAL_ERROR,
            message
        );
        
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }
    
    /**
     * 处理资源未找到异
     * 
     * @param ex 未找到异
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        String message = String.format("资源未找 %s %s", 
            ex.getHttpMethod(), ex.getRequestURL());
        
        log.warn("[GlobalExceptionHandler] {}", message);
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.RESOURCE_NOT_FOUND,
            message
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 兜底处理未知异常
     * 
     * @param ex 异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, HttpServletRequest request) {
        
        log.error("[GlobalExceptionHandler] 未知异常, path: {}", 
            request.getRequestURI(), ex);
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INTERNAL_ERROR,
            "系统内部错误，请稍后重试"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
