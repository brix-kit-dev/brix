/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.query;

import io.runtime.sdk.annotation.Since;

/**
 * Calls one typed query of another plugin (root BRX-RS-MESSAGE-002 to MESSAGE-008).
 *
 * <p>The call runs on the calling thread and blocks until the provider answers or the deadline passes. Its
 * caller, tenant scope, access decision, trace and deadline come from the call the plugin is handling; a plugin
 * cannot set or replace them. Outside a call the runtime admitted, such as {@code onStart}, it is refused.</p>
 *
 * @param <Q> request type
 * @param <R> result type
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public interface QueryClient<Q, R> {

    /**
     * Runs the query.
     *
     * @param request the request; never {@code null}
     * @return the provider's result; {@code Optional.empty()} when a query that may find nothing finds nothing
     * @throws BrixQueryException with a stable code when the query cannot be answered: the provider is missing,
     *     access is denied, the deadline passed, the call was rejected or the circuit is open, or the provider
     *     failed
     */
    R execute(Q request);

    /**
     * Returns the query this client calls.
     *
     * @return the query type
     */
    QueryType<Q, R> type();
}
