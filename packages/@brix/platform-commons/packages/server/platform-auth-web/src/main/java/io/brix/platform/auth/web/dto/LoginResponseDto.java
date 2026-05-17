/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Unified login response DTO. Two-faced shape:
 *
 * <ul>
 *   <li>{@code step=COMPLETE} — {@code accessToken / refreshToken / user / expiresIn} populated.</li>
 *   <li>{@code step=SELECT_TENANT} — {@code identityToken / tenants} populated; rest may be null.</li>
 * </ul>
 *
 * <p>Field-level nullability matches the corresponding capability {@code LoginResult}.</p>
 *
 * @since 3.2.0
 */
@Schema(name = "LoginResponse", description = "登录响应（step=COMPLETE 返回 token；step=SELECT_TENANT 返回租户列表）")
public record LoginResponseDto(

        @Schema(description = "请求是否成功", example = "true") boolean success,

        @Schema(description = "登录步骤 (COMPLETE / SELECT_TENANT)") String step,

        // ===== COMPLETE 字段 =====
        @Schema(description = "访问令牌 (JWT)") String accessToken,
        @Schema(description = "刷新令牌") String refreshToken,
        @Schema(description = "访问令牌有效期 (秒)") Long expiresIn,
        @Schema(description = "用户视图") UserView user,

        // ===== SELECT_TENANT 字段 =====
        @Schema(description = "Identity Token (用于 select-tenant 调用)") String identityToken,
        @Schema(description = "可选租户列表") List<TenantOptionDto> tenants,

        // ===== 元数据 =====
        @Schema(description = "是否要求强制改密") boolean mustChangePassword,
        @Schema(description = "是否要求 MFA") boolean mfaRequired,
        @Schema(description = "是否平台管理员模式（无租户上下文）") boolean platformAdminMode
) {

    /**
     * 用户视图（仅 step=COMPLETE 时返回）。
     */
    @Schema(name = "LoginUserView", description = "登录后用户视图")
    public record UserView(
            @Schema(description = "Identity ID") Long id,
            @Schema(description = "用户名") String username,
            @Schema(description = "邮箱") String email,
            @Schema(description = "显示名") String name,
            @Schema(description = "主角色") String role,
            @Schema(description = "全部角色") List<String> roles,
            @Schema(description = "权限列表") List<String> permissions,
            @Schema(description = "登录提供方", example = "local") String provider
    ) {}
}
