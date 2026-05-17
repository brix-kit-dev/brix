/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tenant option element returned during the multi-tenant selection step.
 *
 * @since 3.2.0
 */
@Schema(name = "TenantOption", description = "多租户登录可选租户")
public record TenantOptionDto(
        @Schema(description = "租户 ID") Long tenantId,
        @Schema(description = "租户编码") String tenantCode,
        @Schema(description = "租户名称") String tenantName,
        @Schema(description = "角色类型 (actor / subject)") String roleType,
        @Schema(description = "B 端 memberType 或 C 端 principalType") String role,
        @Schema(description = "最近访问时间 (ISO-8601, 可空)") String lastAccessAt
) {}
