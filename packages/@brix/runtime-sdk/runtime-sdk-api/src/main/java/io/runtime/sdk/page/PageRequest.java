/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.runtime.sdk.annotation.Since;

/**
 * Which page of a list a caller asks for (standard-link plan L7.8, D61): the query parameters {@code page}, from 1,
 * {@code size}, at most {@value #MAX_SIZE}, and {@code sort}, each {@code <property>} or {@code <property>,desc}, from
 * the values the endpoint's request schema enumerates.
 *
 * @param page the page, from 1
 * @param size how many items a page holds, 1 to {@value #MAX_SIZE}
 * @param sort the order, first criterion first
 * @since 3.3.0
 */
@Since("3.3.0")
public record PageRequest(int page, int size, List<Sort> sort) {

    /** The largest page. */
    public static final int MAX_SIZE = 100;

    /** The size of a page when the caller names none. */
    public static final int DEFAULT_SIZE = 20;

    /**
     * Checks the request.
     */
    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page starts at 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        sort = List.copyOf(Objects.requireNonNull(sort, "sort must not be null"));
    }

    /**
     * Returns the request the query parameters name; absent ones take their defaults.
     *
     * @param page {@code page}, or {@code null} for the first
     * @param size {@code size}, or {@code null} for {@value #DEFAULT_SIZE}
     * @param sort {@code sort}, or {@code null} for none
     * @return the request
     * @throws IllegalArgumentException for a page below 1, a size outside 1 to {@value #MAX_SIZE} or a malformed sort
     */
    public static PageRequest of(Integer page, Integer size, List<String> sort) {
        List<Sort> order = new ArrayList<>();
        for (String criterion : sort == null ? List.<String>of() : sort) {
            order.add(Sort.parse(criterion));
        }
        return new PageRequest(page == null ? 1 : page, size == null ? DEFAULT_SIZE : size, order);
    }

    /**
     * Returns how many items come before this page.
     *
     * @return the offset
     */
    public long offset() {
        return (long) (page - 1) * size;
    }

    /**
     * One sort criterion.
     *
     * @param property the property
     * @param descending whether larger values come first
     */
    public record Sort(String property, boolean descending) {

        /**
         * Checks the criterion.
         */
        public Sort {
            if (property == null || property.isBlank()) {
                throw new IllegalArgumentException("a sort criterion names a property");
            }
        }

        /**
         * Reads {@code <property>}, {@code <property>,asc} or {@code <property>,desc}.
         *
         * @param criterion the query parameter's value
         * @return the criterion
         */
        public static Sort parse(String criterion) {
            Objects.requireNonNull(criterion, "criterion must not be null");
            String[] parts = criterion.split(",", -1);
            if (parts.length > 2) {
                throw new IllegalArgumentException("sort is <property> or <property>,asc|desc: " + criterion);
            }
            String direction = parts.length == 2 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                throw new IllegalArgumentException("sort is <property> or <property>,asc|desc: " + criterion);
            }
            return new Sort(parts[0].trim(), "desc".equals(direction));
        }
    }
}
