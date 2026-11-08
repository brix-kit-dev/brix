/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.internal;

/**
 * Command for creating the first formal platform super administrator.
 *
 * <p>Contract 2.0.0 (standard-link plan point 10b-7a, D99): the runtime verifies the BOOTSTRAP_SETUP session before
 * the handler runs, and the handler names the verified session, never the raw token. The owner checks that it is the
 * session it opened last, unused and unexpired.</p>
 *
 * @param bootstrapSessionId the verified bootstrap session: the {@code jti} of its token
 * @param username display name
 * @param email email address
 * @param notes optional operator note
 */
public record CreateFirstPlatformAdminCommand(
        String bootstrapSessionId,
        String username,
        String email,
        String notes) {

    public CreateFirstPlatformAdminCommand {
        if (bootstrapSessionId == null || bootstrapSessionId.isBlank()) {
            throw new IllegalArgumentException("bootstrapSessionId is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
    }
}
