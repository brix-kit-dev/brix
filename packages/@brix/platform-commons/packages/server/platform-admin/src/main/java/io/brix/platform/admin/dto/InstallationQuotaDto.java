/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.admin.dto;

import java.time.OffsetDateTime;

/**
 * Read-only installation license and tenant quota view.
 *
 * @param installationId deployment installation ID
 * @param quota maximum active tenants allowed
 * @param used current active tenants counted by the quota row
 * @param licenseStatus license state summary
 * @param expiresAt license expiry timestamp, or {@code null} when non-expiring
 * @param canCreateTenant whether the installation can admit another tenant
 * @param refusalReason stable refusal reason code when admission is denied
 * @param updatedAt last quota row update timestamp
 */
public record InstallationQuotaDto(
        String installationId,
        int quota,
        int used,
        String licenseStatus,
        OffsetDateTime expiresAt,
        boolean canCreateTenant,
        String refusalReason,
        OffsetDateTime updatedAt
) {}