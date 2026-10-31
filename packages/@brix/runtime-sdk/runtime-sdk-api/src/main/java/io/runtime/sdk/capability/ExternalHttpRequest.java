/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.runtime.sdk.annotation.Since;

/**
 * A request to an external service (standard-link plan L8.1): a method, a path relative to the service's address,
 * query parameters, headers and a body.
 *
 * <p>The runtime owns the target and the credentials: a path is absolute within the service ({@code /v1/items}) and
 * names no scheme, host, {@code ..} segment, query or fragment, and the headers carry no credentials, cookies, trace
 * context or connection headers.</p>
 *
 * @param method {@code GET}, {@code HEAD}, {@code POST}, {@code PUT}, {@code PATCH} or {@code DELETE}
 * @param path the path within the service, starting with {@code /}
 * @param query the query parameters
 * @param headers the headers, names in lower case
 * @param body the body, empty when there is none
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public record ExternalHttpRequest(String method, String path, Map<String, List<String>> query,
        Map<String, List<String>> headers, byte[] body) {

    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");

    /** Headers the runtime sets or refuses: credentials, cookies, trace context and connection headers. */
    private static final Set<String> RESERVED_HEADERS = Set.of("authorization", "proxy-authorization", "cookie",
        "host", "content-length", "connection", "keep-alive", "transfer-encoding", "upgrade", "te", "expect",
        "traceparent", "tracestate", "baggage");

    /**
     * Checks the request.
     *
     * @throws IllegalArgumentException when the method, the path or a header is not allowed
     */
    public ExternalHttpRequest {
        method = Objects.requireNonNull(method, "method must not be null").trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw new IllegalArgumentException("method must be one of " + METHODS + ": " + method);
        }
        path = checkPath(path);
        query = copy(query, false);
        headers = copy(headers, true);
        body = body == null ? new byte[0] : body.clone();
    }

    /**
     * Returns a {@code GET} of a path.
     *
     * @param path the path within the service
     * @return the request
     */
    public static ExternalHttpRequest get(String path) {
        return new ExternalHttpRequest("GET", path, Map.of(), Map.of(), null);
    }

    /**
     * Returns a {@code POST} of a JSON body.
     *
     * @param path the path within the service
     * @param json the body
     * @return the request, with {@code content-type: application/json}
     */
    public static ExternalHttpRequest postJson(String path, String json) {
        return new ExternalHttpRequest("POST", path, Map.of(),
            Map.of("content-type", List.of("application/json")),
            Objects.requireNonNull(json, "json must not be null").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns this request with one more query parameter value.
     *
     * @param name the parameter
     * @param value its value
     * @return the request
     */
    public ExternalHttpRequest withQuery(String name, String value) {
        return new ExternalHttpRequest(method, path, add(query, name, value), headers, body);
    }

    /**
     * Returns this request with one more header value.
     *
     * @param name the header
     * @param value its value
     * @return the request
     * @throws IllegalArgumentException when the header is one the runtime sets or refuses
     */
    public ExternalHttpRequest withHeader(String name, String value) {
        return new ExternalHttpRequest(method, path, query, add(headers, name, value), body);
    }

    /**
     * Returns a copy of the body.
     *
     * @return the body, empty when there is none
     */
    @Override
    public byte[] body() {
        return body.clone();
    }

    private static String checkPath(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("path must start with a single /: " + path);
        }
        if (path.indexOf('?') >= 0 || path.indexOf('#') >= 0 || path.indexOf('\\') >= 0 || path.contains("://")) {
            throw new IllegalArgumentException("path must not name a query, a fragment or another host: " + path);
        }
        for (String segment : path.split("/")) {
            String plain = segment.toLowerCase(Locale.ROOT).replace("%2e", ".");
            if (".".equals(plain) || "..".equals(plain)) {
                throw new IllegalArgumentException("path must not have . or .. segments: " + path);
            }
        }
        return path;
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> values, boolean headerNames) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        values.forEach((name, list) -> {
            String key = Objects.requireNonNull(name, "names must not be null").trim();
            if (headerNames) {
                key = key.toLowerCase(Locale.ROOT);
                if (RESERVED_HEADERS.contains(key)) {
                    throw new IllegalArgumentException("header " + key + " is set by the runtime or not allowed");
                }
            }
            if (key.isEmpty()) {
                throw new IllegalArgumentException("names must not be blank");
            }
            copy.merge(key, List.copyOf(Objects.requireNonNull(list, "values must not be null")), (first, second) -> {
                List<String> merged = new ArrayList<>(first);
                merged.addAll(second);
                return List.copyOf(merged);
            });
        });
        return Map.copyOf(copy);
    }

    private static Map<String, List<String>> add(Map<String, List<String>> values, String name, String value) {
        Map<String, List<String>> copy = new LinkedHashMap<>(values);
        copy.merge(name, List.of(Objects.requireNonNull(value, "value must not be null")), (first, second) -> {
            List<String> merged = new ArrayList<>(first);
            merged.addAll(second);
            return merged;
        });
        return copy;
    }
}
