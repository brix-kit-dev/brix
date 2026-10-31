/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import io.runtime.sdk.plugin.TraceContext;

/**
 * The server span of an HTTP endpoint call (root BRX-RS-MANIFEST-022, OTEL-002, OTEL-004, ERROR-013; standard-link
 * plan L7.6).
 *
 * <p>An HTTP caller is an untrusted upstream. Each call therefore starts a new trace; a caller's {@code traceparent}
 * that is well formed is at most linked to it, never its parent, and its {@code tracestate} and {@code x-trace-id}
 * are not used. The queries, commands and events the handler sends carry the call's own trace.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface EndpointTracing {

    /** Starts no spans: each call gets a new trace id and nothing is recorded. */
    EndpointTracing NOOP = (route, caller) -> EndpointSpan.untraced();

    /**
     * Starts the server span of a call, in a new trace.
     *
     * @param route the matched route
     * @param caller the caller's trace context when it sent a well-formed one ({@link #caller}); a link at most
     * @return the started span, which the dispatcher closes on the thread that started it
     */
    EndpointSpan server(EndpointRoute route, Optional<TraceContext> caller);

    /**
     * Checks the {@code traceparent} a caller sent: version {@code 00}, lower-case hex, neither id all zeros.
     *
     * @param traceparent the header's value, or {@code null}
     * @return the caller's trace context without its trace state, or empty when the value is missing or malformed
     */
    static Optional<TraceContext> caller(String traceparent) {
        if (traceparent == null || !Traceparents.WELL_FORMED.matcher(traceparent.trim()).matches()) {
            return Optional.empty();
        }
        String value = traceparent.trim();
        if (value.substring(3, 35).equals(Traceparents.ZERO_TRACE)
                || value.substring(36, 52).equals(Traceparents.ZERO_SPAN)) {
            return Optional.empty();
        }
        return Optional.of(new TraceContext(value, null));
    }

    /**
     * Returns the trace id of a trace context.
     *
     * @param trace a trace context whose {@code traceparent} is well formed
     * @return its 32 hex digits
     */
    static String traceId(TraceContext trace) {
        return trace.traceparent().substring(3, 35);
    }

    /**
     * A started server span.
     */
    interface EndpointSpan extends AutoCloseable {

        /**
         * Returns the call's own trace context, which the handler's queries, commands and events carry.
         *
         * @return the trace context of the server span
         */
        TraceContext trace();

        /**
         * Records how the call was answered.
         *
         * @param status the HTTP status
         * @param errorCode the error's wire code, or {@code null} when the call succeeded
         */
        default void finished(int status, String errorCode) {
        }

        /**
         * Ends the span.
         */
        @Override
        default void close() {
        }

        /**
         * Returns a span that records nothing, in a new trace.
         *
         * @return the span
         */
        static EndpointSpan untraced() {
            TraceContext trace = Traceparents.random();
            return () -> trace;
        }
    }

    /** W3C trace context helpers. */
    final class Traceparents {

        static final Pattern WELL_FORMED = Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
        static final String ZERO_TRACE = "0".repeat(32);
        static final String ZERO_SPAN = "0".repeat(16);
        private static final SecureRandom RANDOM = new SecureRandom();

        private Traceparents() {
        }

        /**
         * Returns a new, sampled trace context with random ids.
         *
         * @return the trace context
         */
        public static TraceContext random() {
            byte[] trace = new byte[16];
            byte[] span = new byte[8];
            RANDOM.nextBytes(trace);
            RANDOM.nextBytes(span);
            trace[0] |= 1;
            span[0] |= 1;
            return new TraceContext("00-" + HexFormat.of().formatHex(trace) + "-" + HexFormat.of().formatHex(span)
                + "-01", null);
        }
    }
}
