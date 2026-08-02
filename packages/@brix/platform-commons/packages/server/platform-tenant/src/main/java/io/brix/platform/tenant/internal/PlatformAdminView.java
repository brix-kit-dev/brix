/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

import java.time.OffsetDateTime;

/**
 * Owner-side read view for a platform administrator.
 */
public record PlatformAdminView(
        Long adminId,
        Long identityId,
        String username,
        String email,
        String role,
        String status,
        boolean mfaEnabled,
        String notes,
        OffsetDateTime createdAt
) {
}
