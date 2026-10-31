/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.net.URI;
import java.util.Optional;

import io.runtime.sdk.plugin.TraceContext;

/**
 * The client span of a call to an external service (root BRX-RS-OTEL-002, OTEL-005; standard-link plan L8.4).
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface ExternalHttpTracing {

    /** Starts no spans: the call carries the trace of the call the plugin is handling, when there is one. */
    ExternalHttpTracing NOOP = (pluginId, serviceId, method, target, parent) -> () -> parent;

    /**
     * Starts the client span of one attempt.
     *
     * @param pluginId the calling module
     * @param serviceId the service
     * @param method the method
     * @param target the service's address, without the request's path or query
     * @param parent the trace of the call the plugin is handling, when there is one
     * @return the started span, which the gateway closes on the thread that started it
     */
    ExternalSpan client(String pluginId, String serviceId, String method, URI target, Optional<TraceContext> parent);

    /**
     * A started client span.
     */
    interface ExternalSpan extends AutoCloseable {

        /**
         * Returns the trace context the request carries.
         *
         * @return the span's context, or the parent's, or empty
         */
        Optional<TraceContext> trace();

        /**
         * Records the answer's status.
         *
         * @param status the HTTP status
         */
        default void answered(int status) {
        }

        /**
         * Records that the attempt got no answer.
         *
         * @param errorCode the failure's wire code
         * @param failure the cause
         */
        default void failed(String errorCode, Throwable failure) {
        }

        @Override
        default void close() {
        }
    }
}
