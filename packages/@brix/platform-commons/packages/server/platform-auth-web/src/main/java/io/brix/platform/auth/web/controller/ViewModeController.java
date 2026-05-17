/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.controller;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.auth.web.dto.ErrorResponseDto;
import io.brix.platform.auth.web.dto.ViewModeSwitchRequestDto;
import io.brix.platform.auth.web.dto.ViewModeSwitchResponseDto;
import io.runtime.sdk.capability.ViewModeCapability;
import io.runtime.sdk.capability.ViewModeCapability.SwitchRequest;
import io.runtime.sdk.capability.ViewModeCapability.SwitchResult;
import io.runtime.sdk.capability.ViewModeCapability.ViewMode;
import io.runtime.sdk.capability.ViewModeResolutionException;
import io.runtime.sdk.capability.ViewModeSwitchDeniedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * <h2>View Mode REST Controller — Phase 2 / C-4</h2>
 *
 * <p>Pure delegation to {@link ViewModeCapability}. Hosts the
 * {@code POST /api/auth/view-mode/switch} endpoint that platform admins call
 * to bind their session to a specific tenant view (or to exit a viewing
 * session by switching back to {@link ViewMode#PLATFORM_ADMIN}).</p>
 *
 * <h3>Why a dedicated controller (not merged into AuthController)</h3>
 * <p>The legacy {@code AuthController} is concerned with credential exchange
 * (login / refresh / logout). View-mode switching is a privileged
 * post-authentication operation governed by a separate capability and
 * deserves its own audit-trail surface and OpenAPI tag.</p>
 *
 * @since 3.3.0
 * @see ViewModeCapability
 */
@Tag(name = "View Mode", description = "平台超管视角切换 (Phase 2 / C-4)")
@RestController
@RequestMapping("/api/auth/view-mode")
public class ViewModeController {

        private static final String TRACE_ID_MDC_KEY = "traceId";
        private static final String TRACE_ID_HEADER = "X-Trace-Id";
        private static final String TRACE_ID_REQUEST_ATTRIBUTE =
                        ViewModeController.class.getName() + ".traceId";

    private final ViewModeCapability viewModeCapability;

    public ViewModeController(ViewModeCapability viewModeCapability) {
        this.viewModeCapability = viewModeCapability;
    }

    @Operation(summary = "切换平台超管视角",
            description = "平台超管 (PLATFORM_ADMIN) 可临时绑定到某个租户视角进行支持/调试，"
                    + "也可通过 mode=PLATFORM_ADMIN 退出视角回到平台视图。后端会:"
                    + "1) 校验调用者具有 platform-admin 身份;"
                    + "2) 重新签发 JWT (保留 platform-admin role + 携带 tenant_id + original_sub 声明);"
                    + "3) 写入 VIEW_MODE_SWITCH 审计日志。"
                    + "前端在收到响应后必须替换 access token 并执行整页刷新。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "切换成功",
                    content = @Content(schema = @Schema(implementation = ViewModeSwitchResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "请求参数无效 (mode 非法 / tenantId 缺失)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "未认证 (无有效 JWT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "调用者非 platform-admin 或原始身份不可用",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/switch")
    public ResponseEntity<ViewModeSwitchResponseDto> switchViewMode(
            @Valid @RequestBody ViewModeSwitchRequestDto request,
            HttpServletRequest servletRequest) {
        String previousTraceId = MDC.get(TRACE_ID_MDC_KEY);
        String traceId = resolveTraceId(servletRequest, previousTraceId);
        servletRequest.setAttribute(TRACE_ID_REQUEST_ATTRIBUTE, traceId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        try {
            ViewMode targetMode = parseMode(request.mode());
            SwitchResult result = viewModeCapability.switchTo(
                    new SwitchRequest(targetMode, request.tenantId()));
            return ResponseEntity.ok()
                    .header(TRACE_ID_HEADER, traceId)
                    .body(new ViewModeSwitchResponseDto(
                            result.accessToken(),
                            result.expiresInSeconds(),
                            result.mode().name(),
                            result.tenantId(),
                            result.originalSub()));
        } finally {
            restoreTraceId(previousTraceId);
        }
    }

    /**
     * Maps the wire-level mode string to {@link ViewMode}, returning
     * {@code 400 Bad Request} if the value is not a recognised enum constant.
     */
    private static ViewMode parseMode(String raw) {
        try {
            return ViewMode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown view mode: " + raw
                            + " (expected PLATFORM_ADMIN | TENANT_ACTOR | TENANT_SUBJECT)",
                    ex);
        }
    }

    @ExceptionHandler(ViewModeSwitchDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleDenied(
            ViewModeSwitchDeniedException ex,
            HttpServletRequest request) {
        return errorResponse(HttpStatus.FORBIDDEN,
                ErrorResponseDto.of("VIEW_MODE_DENIED", ex.getMessage()), request);
    }

    @ExceptionHandler(ViewModeResolutionException.class)
    public ResponseEntity<ErrorResponseDto> handleResolution(
            ViewModeResolutionException ex,
            HttpServletRequest request) {
        return errorResponse(HttpStatus.UNAUTHORIZED,
                ErrorResponseDto.of("VIEW_MODE_UNRESOLVED", ex.getMessage()), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleBadRequest(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST,
                ErrorResponseDto.of("VIEW_MODE_BAD_REQUEST", ex.getMessage()), request);
    }

    private static ResponseEntity<ErrorResponseDto> errorResponse(
            HttpStatus status,
            ErrorResponseDto body,
            HttpServletRequest request) {
        return ResponseEntity.status(status.value())
                .header(TRACE_ID_HEADER, traceIdFromRequest(request))
                .body(body);
    }

    private static String resolveTraceId(HttpServletRequest request, String mdcTraceId) {
        if (hasText(mdcTraceId)) {
            return mdcTraceId;
        }
        String headerTraceId = request.getHeader(TRACE_ID_HEADER);
        if (hasText(headerTraceId)) {
            return headerTraceId;
        }
        return UUID.randomUUID().toString();
    }

    private static String traceIdFromRequest(HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_REQUEST_ATTRIBUTE);
        if (traceId instanceof String value && hasText(value)) {
            return value;
        }
        String mdcTraceId = MDC.get(TRACE_ID_MDC_KEY);
        return hasText(mdcTraceId) ? mdcTraceId : UUID.randomUUID().toString();
    }

    private static void restoreTraceId(String previousTraceId) {
        if (previousTraceId == null) {
            MDC.remove(TRACE_ID_MDC_KEY);
            return;
        }
        MDC.put(TRACE_ID_MDC_KEY, previousTraceId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
