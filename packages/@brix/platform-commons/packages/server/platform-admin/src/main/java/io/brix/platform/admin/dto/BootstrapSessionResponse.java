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

/** Response containing the short-lived Bootstrap Setup token. */
public record BootstrapSessionResponse(
        String tokenType,
        String accessToken,
        long expiresIn
) {}