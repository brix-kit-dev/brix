/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Context selection request submitted with an Identity Token.
 *
 * <p>The legacy {@code /api/auth/select-tenant} endpoint uses {@code tenantId}.
 * The Phase 2 {@code /api/auth/select-context} endpoint uses
 * {@code selectionTicket} and does not expose stable context IDs.</p>
 *
 * @since 3.2.2
 */
@Schema(name = "SelectTenantRequest", description = "Tenant or context selection request")
public record SelectTenantRequestDto(

        @Schema(description = "Legacy target tenant ID", example = "1001")
        Long tenantId,

        @Schema(description = "Opaque one-time context selection ticket")
        String selectionTicket
) {}
