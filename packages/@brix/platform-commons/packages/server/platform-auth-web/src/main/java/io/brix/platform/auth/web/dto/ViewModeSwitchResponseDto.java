/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * View-Mode switch response — Phase 2 / C-4.
 *
 * <p>Wire shape returned by {@code POST /api/auth/view-mode/switch}. The
 * {@code accessToken} replaces whatever access token the caller currently
 * holds; the front-end is expected to persist it via the auth-storage layer
 * and then trigger a full page reload.</p>
 *
 * @param accessToken      freshly signed access JWT (always populated)
 * @param expiresInSeconds access-token lifetime, in seconds
 * @param mode             the view mode now in effect (literal enum name)
 * @param tenantId         tenant currently being viewed; {@code null} for
 *                         {@code PLATFORM_ADMIN}
 * @param originalSub      platform-admin identity that initiated the view
 *                         session; {@code null} for {@code PLATFORM_ADMIN}
 * @since 3.3.0
 */
@Schema(name = "ViewModeSwitchResponse", description = "平台超管视角切换响应 (Phase 2 / C-4)")
public record ViewModeSwitchResponseDto(

        @Schema(description = "新的 access JWT")
        String accessToken,

        @Schema(description = "access token 有效期 (秒)")
        long expiresInSeconds,

        @Schema(description = "当前视角")
        String mode,

        @Schema(description = "正在查看的租户 ID (PLATFORM_ADMIN 时为 null)")
        Long tenantId,

        @Schema(description = "发起此视角链路的原始平台超管身份 (PLATFORM_ADMIN 时为 null)")
        String originalSub
) {}
