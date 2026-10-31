/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.query;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import io.runtime.sdk.annotation.Since;

/**
 * A typed query contract: its name, its version and the request and result types generated from its schemas.
 *
 * <p>Instances are constants in the providing plugin's api artifact, generated from the query's contract file
 * ({@code META-INF/brix/queries/<queryType>/<version>.json}). The name inside is data the runtime checks, not a
 * lookup key a plugin can choose (root BRX-RS-MESSAGE-009): the runtime accepts a query type only when the calling
 * plugin declares it under {@code queries.consumes} and its request and result types carry the schemas the
 * provider declares.</p>
 *
 * @param <Q> request type
 * @param <R> result type; {@code Optional<...>} of the result type when the query may find nothing
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public final class QueryType<Q, R> {

    /** Query names: the provider's plugin id, a dot, and the query's name. */
    public static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+");

    private static final Pattern VERSION = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

    private final String queryType;
    private final String version;
    private final Class<Q> requestType;
    private final Class<?> resultType;
    private final boolean optionalResult;

    private QueryType(String queryType, String version, Class<Q> requestType, Class<?> resultType,
            boolean optionalResult) {
        this.queryType = requireName(queryType);
        this.version = requireVersion(version);
        this.requestType = Objects.requireNonNull(requestType, "requestType must not be null");
        this.resultType = Objects.requireNonNull(resultType, "resultType must not be null");
        if (resultType == Optional.class) {
            throw new IllegalArgumentException("resultType names the result's own type; use optional(...) for a "
                + "query that may find nothing");
        }
        this.optionalResult = optionalResult;
    }

    /**
     * Defines a query that always returns a result.
     *
     * @param queryType the query's name
     * @param version the contract version, {@code major.minor.patch}
     * @param requestType the generated request type
     * @param resultType the generated result type
     * @param <Q> request type
     * @param <R> result type
     * @return the query type
     */
    public static <Q, R> QueryType<Q, R> of(String queryType, String version, Class<Q> requestType,
            Class<R> resultType) {
        return new QueryType<>(queryType, version, requestType, resultType, false);
    }

    /**
     * Defines a query that may find nothing: its result is {@code Optional.empty()} then (root
     * BRX-RS-MANIFEST-027, ERROR-009).
     *
     * @param queryType the query's name
     * @param version the contract version, {@code major.minor.patch}
     * @param requestType the generated request type
     * @param resultType the generated type of a found result
     * @param <Q> request type
     * @param <R> type of a found result
     * @return the query type
     */
    public static <Q, R> QueryType<Q, Optional<R>> optional(String queryType, String version, Class<Q> requestType,
            Class<R> resultType) {
        return new QueryType<>(queryType, version, requestType, resultType, true);
    }

    /**
     * Returns the query's name.
     *
     * @return the name, such as {@code sample-shipping.find-shipment}
     */
    public String queryType() {
        return queryType;
    }

    /**
     * Returns the contract version.
     *
     * @return {@code major.minor.patch}
     */
    public String version() {
        return version;
    }

    /**
     * Returns the contract's major version.
     *
     * @return the major version
     */
    public int major() {
        return Integer.parseInt(version.substring(0, version.indexOf('.')));
    }

    /**
     * Returns the request type.
     *
     * @return the generated request type
     */
    public Class<Q> requestType() {
        return requestType;
    }

    /**
     * Returns the type of a result; for a query that may find nothing, the type inside the {@code Optional}.
     *
     * @return the generated result type
     */
    public Class<?> resultType() {
        return resultType;
    }

    /**
     * Returns whether the query may find nothing.
     *
     * @return {@code true} when the result is an {@code Optional}
     */
    public boolean optionalResult() {
        return optionalResult;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QueryType<?, ?> that
            && queryType.equals(that.queryType)
            && version.equals(that.version)
            && requestType.equals(that.requestType)
            && resultType.equals(that.resultType)
            && optionalResult == that.optionalResult;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryType, version, requestType, resultType, optionalResult);
    }

    @Override
    public String toString() {
        return queryType + " " + version;
    }

    private static String requireName(String name) {
        if (name == null || !NAME.matcher(name).matches() || name.length() > 120) {
            throw new IllegalArgumentException("queryType must be '<plugin-id>.<name>' in lower case: " + name);
        }
        return name;
    }

    private static String requireVersion(String version) {
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("version must be major.minor.patch: " + version);
        }
        return version;
    }
}
