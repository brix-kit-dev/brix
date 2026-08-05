/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.internal;

import java.util.List;

/**
 * Stable page envelope returned by tenant administration read views.
 *
 * @param content page content
 * @param page zero-based page index
 * @param size page size
 * @param totalElements total matching rows
 * @param totalPages total matching pages
 * @param first whether this is the first page
 * @param last whether this is the last page
 * @param <T> item type
 */
public record PlatformPageView<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
