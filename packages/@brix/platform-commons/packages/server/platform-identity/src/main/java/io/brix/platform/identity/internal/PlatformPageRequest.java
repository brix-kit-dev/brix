/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.internal;

/**
 * Stable zero-based pagination request for platform internal-contract reads.
 */
public record PlatformPageRequest(
        int page,
        int size,
        String sortBy,
        boolean descending,
        String status,
        String query
) {
}
