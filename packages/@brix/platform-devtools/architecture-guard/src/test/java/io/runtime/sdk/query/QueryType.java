/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.sdk.query;

import java.util.Optional;

/** Stand-in for the SDK's typed query contract, under its real name: the guard does not depend on the SDK. */
public final class QueryType<Q, R> {

    private final String queryType;

    private final String version;

    private QueryType(String queryType, String version) {
        this.queryType = queryType;
        this.version = version;
    }

    public static <Q, R> QueryType<Q, R> of(String queryType, String version, Class<Q> requestType,
            Class<R> resultType) {
        return new QueryType<>(queryType, version);
    }

    public static <Q, R> QueryType<Q, Optional<R>> optional(String queryType, String version, Class<Q> requestType,
            Class<R> resultType) {
        return new QueryType<>(queryType, version);
    }

    public String queryType() {
        return queryType;
    }

    public String version() {
        return version;
    }
}
