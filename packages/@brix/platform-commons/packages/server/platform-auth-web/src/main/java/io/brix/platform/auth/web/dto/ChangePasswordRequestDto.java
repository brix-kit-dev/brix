/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Change-password request — used both for forced rotation
 * ({@code mustChangePassword=true}) and voluntary changes.
 *
 * @since 3.2.0
 */
@Schema(name = "ChangePasswordRequest", description = "修改密码请求")
public record ChangePasswordRequestDto(

        @Schema(description = "旧密码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "oldPassword 不能为空")
        String oldPassword,

        @Schema(description = "新密码（最少 8 位）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "newPassword 不能为空")
        @Size(min = 8, max = 128, message = "newPassword 长度必须在 8..128 之间")
        String newPassword
) {}
