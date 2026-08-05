/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.internal;

/**
 * BOOTSTRAP_SETUP session response.
 *
 * @param tokenType stable token type
 * @param accessToken short-lived bootstrap setup token
 * @param expiresIn seconds until expiry
 */
public record BootstrapSessionView(String tokenType, String accessToken, long expiresIn) {
}
