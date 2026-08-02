/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

import java.time.OffsetDateTime;

/**
 * Owner-side installation quota and license admission read view.
 */
public record InstallationQuotaView(
        String installationId,
        int quota,
        int used,
        String licenseStatus,
        OffsetDateTime expiresAt,
        boolean canCreateTenant,
        String refusalReason,
        OffsetDateTime updatedAt
) {
}
