/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.dto;

import java.util.List;

/**
 * Frontend-compatible zero-based page envelope for platform-admin read endpoints.
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
public record PlatformPageDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
