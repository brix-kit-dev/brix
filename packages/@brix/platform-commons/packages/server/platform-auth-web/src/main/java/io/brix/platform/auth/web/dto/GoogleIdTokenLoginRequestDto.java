/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Google ID-Token login request — One-Tap / GIS Sign-In flow.
 *
 * <p>The frontend posts the raw ID Token returned by the Google Identity
 * Services library. The server verifies signature + claims via
 * {@code OAuth2FederationCapability} and federates to a platform identity.</p>
 *
 * @since 3.2.0
 */
@Schema(name = "GoogleIdTokenLoginRequest", description = "Google ID Token 登录请求（One-Tap / GIS）")
public record GoogleIdTokenLoginRequestDto(

        @Schema(description = "Google 颁发的 ID Token (JWT, RS256)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "idToken 不能为空")
        String idToken
) {}
