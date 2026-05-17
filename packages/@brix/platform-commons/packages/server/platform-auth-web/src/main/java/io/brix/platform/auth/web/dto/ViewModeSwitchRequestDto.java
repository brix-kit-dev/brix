/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * View-Mode switch request — Phase 2 / C-4.
 *
 * <p>Payload accepted by {@code POST /api/auth/view-mode/switch}. The
 * {@code mode} value is the literal name of {@code ViewModeCapability.ViewMode}
 * ({@code PLATFORM_ADMIN} / {@code TENANT_ACTOR} / {@code TENANT_SUBJECT}).
 * The {@code tenantId} is required for non-{@code PLATFORM_ADMIN} switches and
 * is rejected for {@code PLATFORM_ADMIN}.</p>
 *
 * @param mode     target view mode (literal enum name)
 * @param tenantId target tenant ID (required when {@code mode != PLATFORM_ADMIN})
 * @since 3.3.0
 */
@Schema(name = "ViewModeSwitchRequest", description = "平台超管视角切换请求 (Phase 2 / C-4)")
public record ViewModeSwitchRequestDto(

        @Schema(description = "目标视角: PLATFORM_ADMIN | TENANT_ACTOR | TENANT_SUBJECT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "mode 不能为空")
        String mode,

        @Schema(description = "目标租户 ID (mode != PLATFORM_ADMIN 时必填)")
        Long tenantId
) {}
