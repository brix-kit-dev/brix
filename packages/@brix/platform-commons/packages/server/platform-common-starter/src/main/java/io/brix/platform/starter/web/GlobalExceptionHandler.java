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
 * v2.1 Global Exception Handler
 * 
 * <p>Uniformly handles all exceptions thrown from controllers, returns standard ApiResponse format</p>
 * 
 * <p>Exception types handled:</p>
 * <ul>
 *   <li>PlatformException - Platform business exception</li>
 *   <li>MethodArgumentNotValidException - Parameter validation exception</li>
 *   <li>BindException - Parameter binding exception</li>
 *   <li>MissingServletRequestParameterException - Missing request parameter</li>
 *   <li>MethodArgumentTypeMismatchException - Parameter type mismatch</li>
 *   <li>HttpRequestMethodNotSupportedException - HTTP method not supported</li>
 *   <li>HttpMediaTypeNotSupportedException - Media type not supported</li>
 *   <li>NoHandlerFoundException - Resource not found</li>
 *   <li>Exception - Fallback handling for unknown exceptions</li>
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
     * Handle platform business exception
     * 
     * @param ex Platform exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformException(
            PlatformException ex, HttpServletRequest request) {
        
        log.warn("[GlobalExceptionHandler] Business exception: {} - {}, path: {}", 
            ex.getErrorCode().getCode(), ex.getMessage(), request.getRequestURI());
        
        // Determine HTTP status code based on error code
        HttpStatus status = HttpStatus.valueOf(ex.getErrorCode().getHttpStatus());
        
        ApiResponse<Void> response = ApiResponse.failure(
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Handle parameter validation exception (triggered by @Valid annotation)
     * 
     * @param ex Parameter validation exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        // Collect all validation errors
        Map<String, String> errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Validation failed",
                (existing, replacement) -> existing
            ));
        
        log.warn("[GlobalExceptionHandler] Parameter validation failed: {}, path: {}", 
            errors, request.getRequestURI());
        
        // Concatenate error info into message
        String errorMessage = "Parameter validation failed: " + errors.entrySet().stream()
            .map(e -> e.getKey() + " - " + e.getValue())
            .collect(Collectors.joining("; "));
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INVALID_PARAMETER,
            errorMessage
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle parameter binding exception (form submission)
     * 
     * @param ex Binding exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException ex, HttpServletRequest request) {
        
        Map<String, String> errors = ex.getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Binding failed",
                (existing, replacement) -> existing
            ));
        
        log.warn("[GlobalExceptionHandler] Parameter binding failed: {}, path: {}", 
            errors, request.getRequestURI());
        
        // Concatenate error info into message
        String errorMessage = "Parameter binding failed: " + errors.entrySet().stream()
            .map(e -> e.getKey() + " - " + e.getValue())
            .collect(Collectors.joining("; "));
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INVALID_PARAMETER,
            errorMessage
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle missing request parameter exception
     * 
     * @param ex Missing parameter exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        
        String message = String.format("Missing required parameter: %s (type: %s)", 
            ex.getParameterName(), ex.getParameterType());
        
        log.warn("[GlobalExceptionHandler] {}, path: {}", message, request.getRequestURI());
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INVALID_PARAMETER,
            message
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle parameter type mismatch exception
     * 
     * @param ex Type mismatch exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String message = String.format("Parameter type mismatch: %s (expected type: %s)", 
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
     * Handle HTTP method not supported exception
     * 
     * @param ex Method not supported exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        
        String message = String.format("HTTP method not supported: %s", ex.getMethod());
        
        log.warn("[GlobalExceptionHandler] {}, path: {}", message, request.getRequestURI());
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INTERNAL_ERROR,
            message
        );
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }
    
    /**
     * Handle media type not supported exception
     * 
     * @param ex Media type not supported exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        
        String message = String.format("Media type not supported: %s", ex.getContentType());
        
        log.warn("[GlobalExceptionHandler] {}, path: {}", message, request.getRequestURI());
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INTERNAL_ERROR,
            message
        );
        
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }
    
    /**
     * Handle resource not found exception
     * 
     * @param ex Not found exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        String message = String.format("Resource not found: %s %s", 
            ex.getHttpMethod(), ex.getRequestURL());
        
        log.warn("[GlobalExceptionHandler] {}", message);
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.RESOURCE_NOT_FOUND,
            message
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Fallback handler for unknown exceptions
     * 
     * @param ex Exception
     * @param request HTTP request
     * @return Error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, HttpServletRequest request) {
        
        log.error("[GlobalExceptionHandler] Unknown exception, path: {}", 
            request.getRequestURI(), ex);
        
        ApiResponse<Void> response = ApiResponse.failure(
            PlatformErrorCode.INTERNAL_ERROR,
            "Internal server error, please try again later"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
