/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.time.Instant;
import java.util.Objects;

import io.runtime.sdk.annotation.Since;

/**
 * One call of a typed query, as the runtime hands it to the provider's {@link QueryHandler}
 * (root BRX-RS-MANIFEST-028).
 *
 * <p>The runtime builds it from the caller's admitted call; the provider must not replace or re-derive any part of
 * it from thread locals, headers or framework state.</p>
 *
 * @param request the request
 * @param caller the calling plugin and the user it acts for
 * @param scope the tenant scope the query runs in
 * @param access the caller's access decision, for data-scope checks inside the provider
 * @param trace the trace context of this call
 * @param deadline when the answer is no longer wanted
 * @param cancellation tells the provider the caller has given up
 * @param <Q> request type
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public record QueryInvocation<Q>(
        Q request,
        CallerIdentity caller,
        InvocationScope scope,
        AccessDecision access,
        TraceContext trace,
        Instant deadline,
        CancellationSignal cancellation) {

    /**
     * Validates the invocation.
     */
    public QueryInvocation {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(caller, "caller must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(access, "access must not be null");
        Objects.requireNonNull(trace, "trace must not be null");
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancellation, "cancellation must not be null");
    }
}
