/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Tenant selection request — submitted with an Identity Token after the
 * login endpoint returns {@code step=SELECT_TENANT}.
 *
 * @since 3.2.0
 */
@Schema(name = "SelectTenantRequest", description = "多租户登录第二步：选择目标租户")
public record SelectTenantRequestDto(

        @Schema(description = "目标租户 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "tenantId 不能为空")
        Long tenantId
) {}
