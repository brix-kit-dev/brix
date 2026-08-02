/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

import java.time.OffsetDateTime;

/**
 * Owner-side read view for platform tenant administration.
 */
public record PlatformTenantView(
        Long tenantId,
        String code,
        String name,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer quotaUsed,
        Integer quotaLimit,
        String licenseStatus,
        String defaultLocale,
        String defaultTimezone,
        String defaultTheme
) {
}
