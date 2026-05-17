/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.auth.web.dto.ErrorResponseDto;
import io.brix.platform.auth.web.dto.GoogleIdTokenLoginRequestDto;
import io.brix.platform.auth.web.dto.LoginResponseDto;
import io.brix.platform.auth.web.mapper.LoginResultMapper;
import io.runtime.sdk.capability.OAuth2FederationCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * <h2>OAuth2 Federation REST Controller — ID-Token Flow</h2>
 *
 * <p>Pure delegation to {@link OAuth2FederationCapability}. Hosts the
 * {@code /api/v1/oauth2/google/id-token} endpoint used by Google One-Tap and
 * Google Identity Services (GIS) Sign-In.</p>
 *
 * <h3>Why a separate controller (not merged into legacy OAuth2Controller)</h3>
 * <p>The existing identity-server's {@code OAuth2Controller} implements the
 * OAuth2 <b>Authorization Code Flow</b> (authorize URL, callback redirect,
 * state/CSRF, unlink, provider-token refresh). Those concerns are NOT covered
 * by {@link OAuth2FederationCapability} (which only verifies a Google-issued
 * ID Token) and stay in the legacy controller until an
 * {@code OAuth2AuthorizationCodeCapability} is introduced in a future release.</p>
 *
 * @since 3.2.0
 */
@Tag(name = "OAuth2 Federation", description = "OAuth2 Federation — ID Token Flow (v3.2.0)")
@RestController
@RequestMapping("/api/v1/oauth2")
public class OAuth2FederationController {

    private final OAuth2FederationCapability federation;

    public OAuth2FederationController(OAuth2FederationCapability federation) {
        this.federation = federation;
    }

    @Operation(summary = "Google ID Token 登录",
            description = "前端通过 Google Identity Services 拿到 ID Token 后调用此端点完成联邦登录。"
                    + "服务端验证 RS256 签名 + email_verified=true，然后查找已绑定的平台身份。"
                    + "未自动建号 — 防账号枚举。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功",
                    content = @Content(schema = @Schema(implementation = LoginResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "ID Token 无效 / email 未验证 / 账号未绑定",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "账号禁用或无租户关联",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "联邦登录能力未配置（缺少 google.client-id）"
                            + " 或多租户联邦待 S5 实现",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/google/id-token")
    public ResponseEntity<LoginResponseDto> loginWithGoogleIdToken(
            @Valid @RequestBody GoogleIdTokenLoginRequestDto request) {
        var result = federation.loginWithGoogleIdToken(request.idToken());
        return ResponseEntity.ok(LoginResultMapper.toDto(result, "google"));
    }
}
