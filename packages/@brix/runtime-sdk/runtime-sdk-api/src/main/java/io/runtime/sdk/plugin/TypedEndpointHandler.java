/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.util.concurrent.CompletionStage;

import io.runtime.sdk.annotation.Since;

/**
 * An HTTP endpoint handler of the root's shape (root BRX-RS-MANIFEST-021 to MANIFEST-024, §5.4; standard-link plan
 * L7.1, D58): {@code CompletionStage<R> handle(EndpointInvocation<B>)}.
 *
 * <p>The runtime builds {@code B} from the request, the body of a {@code POST}, {@code PUT} or {@code PATCH} or the
 * query parameters of a {@code GET} or {@code DELETE}, together with the path variables, after checking them
 * against the manifest's {@code request.schemaRef}. It answers with the manifest's {@code response.status} and the
 * handler's {@code R}, which it checks against {@code response.schemaRef}. The handler writes no status, header or
 * error body: an expected failure throws a {@link io.runtime.sdk.error.BrixException} whose code names its
 * {@link io.runtime.sdk.error.ErrorCategory}.</p>
 *
 * <p>In 4.0 {@link EndpointHandler} takes this shape and this interface becomes its deprecated alias, so a handler
 * written against it does not change.</p>
 *
 * @param <B> the request, as the request schema describes it
 * @param <R> the response, as the response schema describes it
 * @since 3.3.0
 */
@Since("3.3.0")
@FunctionalInterface
public interface TypedEndpointHandler<B, R> extends EndpointHandler<EndpointInvocation<B>, CompletionStage<R>> {

    /**
     * Handles one request.
     *
     * @param invocation the checked request, the verified caller, tenant, trace, deadline and cancellation
     * @return completes with the response, or exceptionally with the failure
     */
    @Override
    CompletionStage<R> handle(EndpointInvocation<B> invocation);
}
