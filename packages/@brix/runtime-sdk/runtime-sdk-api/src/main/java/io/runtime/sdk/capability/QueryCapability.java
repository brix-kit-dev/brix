/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;
import io.runtime.sdk.query.QueryClient;
import io.runtime.sdk.query.QueryType;

/**
 * Reads data another plugin owns through its typed query (root BRX-RS-MESSAGE-002 to MESSAGE-009, §4.7).
 *
 * <p>A plugin names, under {@code queries.consumes}, each query it calls, and gets a typed client for it here,
 * usually in a handler factory. The {@link QueryType} comes from the providing plugin's api artifact, generated
 * from the query's schemas. The runtime does the rest: it derives the caller, the tenant and the deadline from
 * the call the plugin is handling, applies the provider's access policy, its concurrency limit and a circuit
 * breaker, suspends the caller's transaction and runs the provider in a read-only unit of work of its own.</p>
 *
 * <p>A query is a synchronous, read-only call inside this host. It goes through no outbox and no message
 * broker; to make another plugin change its data use {@link CommandCapability}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public interface QueryCapability {

    /**
     * Returns the client of a query this plugin declares under {@code queries.consumes}.
     *
     * @param type the query, as the provider's api artifact defines it
     * @param <Q> request type
     * @param <R> result type, {@code Optional<...>} for a query that may find nothing
     * @return a client bound to this plugin and the query
     * @throws IllegalStateException when this plugin does not declare the query, or its types differ from the
     *     ones the provider declares; the runtime fails the plugin's start in that case
     */
    <Q, R> QueryClient<Q, R> client(QueryType<Q, R> type);
}
