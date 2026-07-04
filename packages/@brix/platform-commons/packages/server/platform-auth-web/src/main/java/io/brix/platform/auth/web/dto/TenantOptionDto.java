/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tenant context option returned during the context selection step.
 *
 * @since 3.2.2
 */
@Schema(name = "TenantOption", description = "Selectable tenant context")
public record TenantOptionDto(
        @Schema(description = "Tenant ID") Long tenantId,
        @Schema(description = "Tenant code") String tenantCode,
        @Schema(description = "Tenant display name") String tenantName,
        @Schema(description = "Role type: actor or subject") String roleType,
        @Schema(description = "Member type or principal type") String role,
        @Schema(description = "Last access time in ISO-8601 format") String lastAccessAt,
        @Schema(description = "Opaque one-time context selection ticket") String selectionTicket
) {}
