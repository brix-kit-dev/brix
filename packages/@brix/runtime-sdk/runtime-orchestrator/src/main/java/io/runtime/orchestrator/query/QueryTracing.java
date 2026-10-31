/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import io.runtime.sdk.plugin.TraceContext;

/**
 * The spans of typed queries (root BRX-RS-OTEL-002, OTEL-005; query design §5.7).
 *
 * <p>The gateway starts a CLIENT span for each call, a child of the caller's current context or of the trace
 * context its entry carried, and the provider runs in a SERVER span, a child of the CLIENT span. An adapter, such as
 * the OpenTelemetry one, records them; {@link #NOOP}, the default, records nothing and gives each query a child
 * trace context of its caller's, so the trace id still runs through.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface QueryTracing {

    /** Records no span. */
    QueryTracing NOOP = new QueryTracing() {
    };

    /**
     * Starts the CLIENT span of a call.
     *
     * @param consumerPluginId the calling plugin
     * @param providerPluginId the providing plugin
     * @param queryType the query type
     * @param parent the trace context the caller's entry carried, if any
     * @return the span; the provider gets its trace context
     */
    default QuerySpan client(String consumerPluginId, String providerPluginId, String queryType,
            Optional<TraceContext> parent) {
        return QuerySpan.unrecorded(parent);
    }

    /**
     * Starts the SERVER span the provider runs in, a child of the CLIENT span of the call.
     *
     * @param client the trace context of the CLIENT span
     * @param providerPluginId the providing plugin
     * @param queryType the query type
     * @return the span
     */
    default QuerySpan server(TraceContext client, String providerPluginId, String queryType) {
        return QuerySpan.unrecorded(Optional.of(client));
    }

    /**
     * A started span, ended by {@link #close()}.
     */
    interface QuerySpan extends AutoCloseable {

        /**
         * Returns the W3C {@code traceparent} of the span.
         *
         * @return the traceparent, never blank
         */
        String traceparent();

        /**
         * Returns the W3C {@code tracestate} that goes with {@link #traceparent()}.
         *
         * @return the tracestate, or {@code null}
         */
        String tracestate();

        /**
         * Marks the span failed.
         *
         * @param errorCode the stable query error code
         * @param failure why
         */
        default void failed(String errorCode, Throwable failure) {
        }

        /** Ends the span. */
        @Override
        default void close() {
        }

        /**
         * Returns a span that records nothing: a child context of {@code parent}, or a new trace without one.
         *
         * @param parent the parent context, if any
         * @return the span
         */
        static QuerySpan unrecorded(Optional<TraceContext> parent) {
            String traceparent = childOf(parent.map(TraceContext::traceparent).orElse(null));
            String tracestate = parent.map(TraceContext::tracestate).orElse(null);
            return new QuerySpan() {
                @Override
                public String traceparent() {
                    return traceparent;
                }

                @Override
                public String tracestate() {
                    return tracestate;
                }
            };
        }

        /**
         * Returns a traceparent with the trace id of {@code traceparent} and a new span id, or a new trace when
         * {@code traceparent} is missing or malformed.
         *
         * @param traceparent the parent's traceparent
         * @return the child traceparent
         */
        static String childOf(String traceparent) {
            HexFormat hex = HexFormat.of();
            byte[] span = new byte[8];
            ThreadLocalRandom.current().nextBytes(span);
            span[0] |= 1;
            if (traceparent != null && Holder.TRACEPARENT.matcher(traceparent).matches()) {
                return "00-" + traceparent.substring(3, 35) + "-" + hex.formatHex(span) + "-" + traceparent.substring(53);
            }
            byte[] trace = new byte[16];
            ThreadLocalRandom.current().nextBytes(trace);
            trace[0] |= 1;
            return "00-" + hex.formatHex(trace) + "-" + hex.formatHex(span) + "-01";
        }
    }

    /** Patterns shared by the default spans. */
    final class Holder {

        /** A W3C traceparent of version 00 with a non-zero trace id. */
        static final Pattern TRACEPARENT = Pattern.compile("00-(?!0{32})[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

        private Holder() {
        }
    }
}
