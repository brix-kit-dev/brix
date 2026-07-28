/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

/**
 * Command for completing password and TOTP setup.
 *
 * @param setupToken raw setup token from the secure link
 * @param challengeId TOTP setup challenge id
 * @param password new password
 * @param totpCode six-digit TOTP code
 */
public record CompletePlatformSetupCommand(
        String setupToken,
        String challengeId,
        String password,
        String totpCode) {

    public CompletePlatformSetupCommand {
        if (setupToken == null || setupToken.isBlank()) {
            throw new IllegalArgumentException("setupToken is required");
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("challengeId is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (totpCode == null || totpCode.isBlank()) {
            throw new IllegalArgumentException("totpCode is required");
        }
    }
}
