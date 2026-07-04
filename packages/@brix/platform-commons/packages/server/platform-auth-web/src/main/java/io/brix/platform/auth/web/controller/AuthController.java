/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.auth.web.dto.ChangePasswordRequestDto;
import io.brix.platform.auth.web.dto.ErrorResponseDto;
import io.brix.platform.auth.web.dto.LoginRequestDto;
import io.brix.platform.auth.web.dto.LoginResponseDto;
import io.brix.platform.auth.web.dto.RefreshTokenRequestDto;
import io.brix.platform.auth.web.dto.SelectTenantRequestDto;
import io.brix.platform.auth.web.mapper.LoginResultMapper;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.ChangePasswordCommand;
import io.runtime.sdk.capability.AuthFlowCapability.LoginCommand;
import io.runtime.sdk.capability.AuthFlowCapability.RefreshCommand;
import io.runtime.sdk.capability.AuthFlowCapability.SelectContextCommand;
import io.runtime.sdk.capability.AuthFlowCapability.SelectTenantCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * <h2>Authentication REST Controller — Layer 2C HTTP binding</h2>
 *
 * <p>Pure delegation to {@link AuthFlowCapability}. All business logic
 * (multi-tenant decision, BCrypt verify, token issuance) lives in the
 * capability implementation; this controller only handles HTTP concerns
 * (DTO conversion, principal extraction).</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/auth/login} — username/email + password (multi-step capable)</li>
 *   <li>{@code POST /api/auth/select-tenant} — second step after {@code SELECT_TENANT}</li>
 *   <li>{@code POST /api/auth/refresh} — refresh token rotation</li>
 *   <li>{@code POST /api/auth/change-password} — change password (forced or voluntary)</li>
 * </ul>
 *
 * <h3>Error mapping</h3>
 * <p>All {@link AuthFlowException} instances are translated to
 * {@code ErrorResponseDto} via {@code AuthFlowExceptionAdvice}.</p>
 *
 * @since 3.2.0
 */
@Tag(name = "Auth", description = "Platform Authentication API (v3.2.0)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthFlowCapability authFlow;
    private final AuthContextCapability authContext;
    private final SecurityContextHolder securityContextHolder;

    public AuthController(AuthFlowCapability authFlow,
                          AuthContextCapability authContext,
                          SecurityContextHolder securityContextHolder) {
        this.authFlow = authFlow;
        this.authContext = authContext;
        this.securityContextHolder = securityContextHolder;
    }

    @Operation(summary = "用户名/密码登录",
            description = "返回访问令牌或多租户选择列表。step=COMPLETE 时直接返回 Access Token；step=SELECT_TENANT 时返回 Identity Token + 租户列表。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功或需要选择租户",
                    content = @Content(schema = @Schema(implementation = LoginResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "凭据错误",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "账户禁用 / 锁定 / 无租户",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request,
                                                  HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        LoginCommand cmd = new LoginCommand(request.loginId(), request.password(), clientIp);
        var result = authFlow.login(cmd);
        return ResponseEntity.ok(LoginResultMapper.toDto(result, "local"));
    }

    @PostMapping("/login/actor")
    public ResponseEntity<LoginResponseDto> loginActor(@Valid @RequestBody LoginRequestDto request,
                                                       HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        LoginCommand cmd = new LoginCommand(request.loginId(), request.password(), clientIp);
        var result = authFlow.loginActor(cmd);
        return ResponseEntity.ok(LoginResultMapper.toDto(result, "local"));
    }

    @PostMapping("/login/subject")
    public ResponseEntity<LoginResponseDto> loginSubject(@Valid @RequestBody LoginRequestDto request,
                                                         HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        LoginCommand cmd = new LoginCommand(request.loginId(), request.password(), clientIp);
        var result = authFlow.loginSubject(cmd);
        return ResponseEntity.ok(LoginResultMapper.toDto(result, "local"));
    }

    @Operation(summary = "选择租户（多租户登录第二步）",
            description = "携带 Identity Token 提交目标租户 ID，换取该租户的 Access Token。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "选择成功"),
            @ApiResponse(responseCode = "401", description = "Identity Token 无效或缺失",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "无权访问目标租户",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/select-tenant")
    public ResponseEntity<LoginResponseDto> selectTenant(@Valid @RequestBody SelectTenantRequestDto request) {
        if (request == null || request.tenantId() == null) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_TENANT_ACCESS_DENIED, "tenantId is required");
        }
        Long identityId = requireIdentityId();
        SelectTenantCommand cmd = new SelectTenantCommand(request.tenantId());
        var result = authFlow.selectTenant(identityId, cmd);
        return ResponseEntity.ok(LoginResultMapper.toDto(result, "local"));
    }

    @PostMapping("/select-context")
    public ResponseEntity<LoginResponseDto> selectContext(@Valid @RequestBody SelectTenantRequestDto request) {
        Long identityId = requireIdentityId();
        SelectContextCommand cmd = new SelectContextCommand(
                request == null ? null : request.selectionTicket(),
                requireIdentityTokenJti());
        var result = authFlow.selectContext(identityId, cmd);
        return ResponseEntity.ok(LoginResultMapper.toDto(result, "local"));
    }

    @Operation(summary = "刷新访问令牌",
            description = "使用 Refresh Token 换取新的 Access Token + Refresh Token 对。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "刷新成功"),
            @ApiResponse(responseCode = "401", description = "Refresh Token 无效或过期",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "Refresh Token 能力暂不可用",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        var result = authFlow.refreshToken(new RefreshCommand(request.refreshToken()));
        return ResponseEntity.ok(LoginResultMapper.toDto(result, "local"));
    }

    @Operation(summary = "修改密码（强制改密或自主改密）",
            description = "首次登录或管理员重置密码后，前端必须调用此端点完成强制改密。")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "修改成功"),
            @ApiResponse(responseCode = "400", description = "旧密码错误或新密码不符合策略",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "未登录",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "改密能力暂未启用 (S3 阶段引入)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDto request) {
        Long identityId = requireIdentityId();
        authFlow.changePassword(new ChangePasswordCommand(identityId, request.oldPassword(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    // ==================== helpers ====================

    private Long requireIdentityId() {
        Principal principal = authContext.getCurrentPrincipal();
        if (principal == null || principal.getName() == null) {
            throw new AuthFlowException(AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                    "Identity Token required (missing or invalid Authorization header)");
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("[Auth] non-numeric principal subject: {}", principal.getName());
            throw new AuthFlowException(AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                    "Invalid identity token (non-numeric subject)");
        }
    }

    private String requireIdentityTokenJti() {
        if (securityContextHolder == null) {
            throw new AuthFlowException(AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                    "Identity Token required (security context unavailable)");
        }
        return securityContextHolder.getCurrentUser()
                .map(user -> user.getJti())
                .filter(jti -> jti != null && !jti.isBlank())
                .orElseThrow(() -> new AuthFlowException(AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                        "Identity Token required (missing jti)"));
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
