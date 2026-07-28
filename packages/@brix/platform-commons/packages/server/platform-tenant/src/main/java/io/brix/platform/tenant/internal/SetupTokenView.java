/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

import java.time.OffsetDateTime;

/**
 * Setup-token validation view without raw token material.
 *
 * @param valid whether the token is usable
 * @param identityId identity id
 * @param email target email
 * @param username target display name
 * @param purpose setup token purpose
 * @param expiresAt expiry timestamp
 */
public record SetupTokenView(
        boolean valid,
        Long identityId,
        String email,
        String username,
        String purpose,
        OffsetDateTime expiresAt) {
}
