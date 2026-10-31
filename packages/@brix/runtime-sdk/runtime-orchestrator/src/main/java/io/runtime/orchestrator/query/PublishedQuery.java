/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.plugin.QueryHandler;

/**
 * A query a started plugin provides, with its handler, the contract types the handler declares and its resolved
 * policies. The gateway admits calls to it only while it is in the Host's published entry generation.
 *
 * @param pluginId the providing plugin
 * @param declaration the manifest declaration
 * @param handler the handler its factory created
 * @param requestType the request type the handler declares
 * @param resultType the result type the handler declares; for a query that may find nothing, the type inside the
 *     {@code Optional}
 * @param optionalResult whether the handler returns an {@code Optional}
 * @param scope the resolved scope policy
 * @param access the resolved access policy
 * @param timeout the provider's timeout
 * @param concurrency the provider's concurrency limit, one permit per running call
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record PublishedQuery(
        String pluginId,
        PluginRuntimeDescriptor.ProvidedQuery declaration,
        QueryHandler<?, ?> handler,
        Class<?> requestType,
        Class<?> resultType,
        boolean optionalResult,
        QueryPolicies.Scope scope,
        QueryPolicies.Access access,
        Duration timeout,
        Semaphore concurrency) {

    /**
     * Validates the entry.
     */
    public PublishedQuery {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        Objects.requireNonNull(declaration, "declaration must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        Objects.requireNonNull(requestType, "requestType must not be null");
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(access, "access must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(concurrency, "concurrency must not be null");
    }

    /**
     * Returns the key the host knows the query by.
     *
     * @return {@code <queryType>#<major>}
     */
    public String key() {
        return declaration.key();
    }

    /**
     * Returns the query type.
     *
     * @return the query type
     */
    public String queryType() {
        return declaration.queryType();
    }
}
