/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.List;

import io.brix.platform.tenant.internal.PlatformPageRequest;
import io.runtime.sdk.plugin.EndpointInvocation;

final class PlatformReadQuerySupport {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private PlatformReadQuerySupport() {
    }

    static PlatformPageRequest pageRequest(EndpointInvocation<?> invocation) {
        PageQuery query = pageQuery(invocation);
        return new PlatformPageRequest(
            query.page(),
            query.size(),
            query.sortBy(),
            query.descending(),
            query.status(),
            query.query());
    }

    static io.brix.platform.identity.internal.PlatformPageRequest identityPageRequest(EndpointInvocation<?> invocation) {
        PageQuery query = pageQuery(invocation);
        return new io.brix.platform.identity.internal.PlatformPageRequest(
            query.page(),
            query.size(),
            query.sortBy(),
            query.descending(),
            query.status(),
            query.query());
    }

    private static PageQuery pageQuery(EndpointInvocation<?> invocation) {
        int page = intQuery(invocation, "page", DEFAULT_PAGE);
        int size = intQuery(invocation, "size", DEFAULT_SIZE);
        SortSpec sort = sortSpec(firstQuery(invocation, "sort", "createdAt,desc"));
        return new PageQuery(
            Math.max(0, page),
            Math.max(1, Math.min(MAX_SIZE, size)),
            sort.field(),
            sort.descending(),
            firstQuery(invocation, "status", null),
            firstQuery(invocation, "q", null));
    }

    private static int intQuery(EndpointInvocation<?> invocation, String name, int defaultValue) {
        String value = firstQuery(invocation, name, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " is invalid", ex);
        }
    }

    private static String firstQuery(EndpointInvocation<?> invocation, String name, String defaultValue) {
        return invocation.queryParameters().getOrDefault(name, List.of()).stream()
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(defaultValue);
    }

    private static SortSpec sortSpec(String value) {
        if (value == null || value.isBlank()) {
            return new SortSpec("createdAt", true);
        }
        String[] parts = value.split(",", 2);
        String field = parts[0].isBlank() ? "createdAt" : parts[0].trim();
        boolean descending = parts.length < 2 || !"asc".equalsIgnoreCase(parts[1].trim());
        return new SortSpec(field, descending);
    }

    private record SortSpec(String field, boolean descending) {
    }

    private record PageQuery(
            int page,
            int size,
            String sortBy,
            boolean descending,
            String status,
            String query) {
    }
}
