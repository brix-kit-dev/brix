/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.internal;

/**
 * Stable zero-based pagination request for tenant administration reads.
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
