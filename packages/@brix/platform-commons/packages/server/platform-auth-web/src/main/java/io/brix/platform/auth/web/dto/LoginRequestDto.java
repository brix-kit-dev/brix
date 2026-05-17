/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Username / email + password login request.
 *
 * @since 3.2.0
 */
@Schema(name = "LoginRequest", description = "用户名/邮箱 + 密码登录请求")
public record LoginRequestDto(

        @Schema(description = "登录标识（邮箱或用户名）", example = "alice@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "loginId 不能为空")
        String loginId,

        @Schema(description = "密码（明文，HTTPS 传输）", example = "P@ssw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "password 不能为空")
        String password
) {}
