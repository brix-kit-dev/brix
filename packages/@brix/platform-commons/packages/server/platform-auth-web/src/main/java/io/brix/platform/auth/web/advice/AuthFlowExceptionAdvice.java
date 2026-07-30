/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.brix.platform.auth.web.dto.ErrorResponseDto;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;

/**
 * Maps {@link AuthFlowException} machine-readable codes to HTTP status codes.
 *
 * <p>The mapping is exhaustive; any unknown future code defaults to 401 (the
 * safest choice for an authentication-flow error).</p>
 *
 * @since 3.2.0
 */
// Scoped to the legacy tenant /api/auth/* controller surface only. Platform
// /api/platform/auth/* is served by Runtime Shell handlers and maps
// AuthFlowException without depending on Spring controller advice.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
        "io.brix.platform.auth.web.controller"
})
public class AuthFlowExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(AuthFlowExceptionAdvice.class);

    @ExceptionHandler(AuthFlowException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthFlow(AuthFlowException ex) {
        HttpStatus status = mapStatus(ex.getErrorCode());
        if (status.is5xxServerError()) {
            log.error("[AuthFlow] {} → {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("[AuthFlow] {} → {}", ex.getErrorCode(), ex.getMessage());
        }
        return ResponseEntity.status(status).body(ErrorResponseDto.of(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        String firstMsg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of("AUTH_VALIDATION_FAILED", firstMsg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of("AUTH_BAD_REQUEST", ex.getMessage()));
    }

    private static HttpStatus mapStatus(String code) {
        if (code == null) return HttpStatus.UNAUTHORIZED;
        return switch (code) {
            case AuthFlowException.CODE_INVALID_CREDENTIALS,
                 AuthFlowException.CODE_INVALID_REFRESH_TOKEN,
                 AuthFlowException.CODE_IDENTITY_NOT_FOUND -> HttpStatus.UNAUTHORIZED;
            case AuthFlowException.CODE_ACCOUNT_DISABLED,
                 AuthFlowException.CODE_TENANT_ACCESS_DENIED,
                 AuthFlowException.CODE_NO_TENANT_ASSOCIATION -> HttpStatus.FORBIDDEN;
              case AuthFlowException.CODE_PENDING_SETUP -> HttpStatus.UNPROCESSABLE_ENTITY;
              case AuthFlowException.CODE_ACCOUNT_LOCKED -> HttpStatus.LOCKED;
            case AuthFlowException.CODE_OLD_PASSWORD_MISMATCH,
                 AuthFlowException.CODE_PASSWORD_POLICY_VIOLATION -> HttpStatus.BAD_REQUEST;
            case AuthFlowException.CODE_CAPABILITY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNAUTHORIZED;
        };
    }
}
