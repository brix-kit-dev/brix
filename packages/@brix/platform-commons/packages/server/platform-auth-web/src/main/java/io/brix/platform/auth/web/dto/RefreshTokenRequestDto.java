/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Refresh-token exchange request.
 *
 * @since 3.2.0
 */
@Schema(name = "RefreshTokenRequest", description = "刷新令牌请求")
public record RefreshTokenRequestDto(

        @Schema(description = "Refresh Token", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {}
