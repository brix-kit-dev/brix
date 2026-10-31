/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.page;

import java.util.List;
import java.util.Objects;

import io.runtime.sdk.annotation.Since;

/**
 * One page of a list (standard-link plan L7.8, D61), as a list endpoint answers: {@code {items, page, size, total}}.
 *
 * @param items the page's items, at most {@code size}
 * @param page the page, from 1
 * @param size the size the caller asked for
 * @param total how many items the whole list has
 * @param <T> item type
 * @since 3.3.0
 */
@Since("3.3.0")
public record Page<T>(List<T> items, int page, int size, long total) {

    /**
     * Checks the page.
     */
    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (page < 1 || size < 1 || size > PageRequest.MAX_SIZE || total < 0 || items.size() > size) {
            throw new IllegalArgumentException("a page has a page from 1, a size from 1 to " + PageRequest.MAX_SIZE
                + ", no more items than its size and a total of at least 0");
        }
    }

    /**
     * Returns the page a request asked for.
     *
     * @param request the request
     * @param items the page's items
     * @param total how many items the whole list has
     * @param <T> item type
     * @return the page
     */
    public static <T> Page<T> of(PageRequest request, List<T> items, long total) {
        return new Page<>(items, request.page(), request.size(), total);
    }
}
