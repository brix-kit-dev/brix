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
 * @param bootstrapSessionToken one-time BOOTSTRAP_SETUP bearer token
 * @param username display name
 * @param email email address
 * @param notes optional operator note
 */
public record CreateFirstPlatformAdminCommand(
        String bootstrapSessionToken,
        String username,
        String email,
        String notes) {

    public CreateFirstPlatformAdminCommand {
        if (bootstrapSessionToken == null || bootstrapSessionToken.isBlank()) {
            throw new IllegalArgumentException("bootstrapSessionToken is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
    }
}
