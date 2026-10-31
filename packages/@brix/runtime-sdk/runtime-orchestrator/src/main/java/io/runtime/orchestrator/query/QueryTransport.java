/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.sdk.plugin.QueryInvocation;

/**
 * Carries one admitted query to its provider (query design §5.10). L2B internal; plugins never see it.
 *
 * <p>The gateway has already admitted, authorized and bounded the call. This host has only the in-process
 * transport; a transport to a provider in another host process would add serialization against the schemas,
 * workload identity and a remote deadline, behind the same gateway.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface QueryTransport {

    /**
     * Runs the query and returns the provider's result.
     *
     * @param query the published query
     * @param invocation the invocation to hand to the provider
     * @param providerCall the call the provider runs in, one level below its caller's
     * @param timeout how long the provider may still take
     * @return the provider's result, not yet checked against the contract
     * @throws QueryDeadlineException when the deadline passed before the provider answered
     * @throws RuntimeException whatever the provider or its unit of work threw
     */
    Object execute(PublishedQuery query, QueryInvocation<Object> invocation, RuntimeInvocation providerCall,
            Duration timeout);

    /**
     * The provider did not answer by the query's deadline.
     */
    final class QueryDeadlineException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates the failure.
         *
         * @param message what passed
         * @param cause the timeout
         */
        public QueryDeadlineException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
