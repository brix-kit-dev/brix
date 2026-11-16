/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.tracing;

import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.MDC;

import io.runtime.sdk.plugin.TraceContext;

/**
 * The trace of a delivered event or command on the thread that delivers it.
 *
 * <p>While the runtime delivers a message to a handler, the thread's logging context (the SLF4J MDC) holds the
 * delivery's {@code traceparent} and its trace id under {@code traceId}, as the endpoint dispatcher does for a request,
 * and gets back what it held before when the delivery ends (root BRX-RS-ERROR-013, ERROR-017 item 7; standard-link
 * plan D133). The handler's unit of work, its commit and the notifications sent at the commit all run inside, so the
 * delivery's log lines, a notification's failure log among them, and the records that read the trace from the thread
 * carry the delivered message's trace; the events, commands and external calls the handler makes continue it (root
 * BRX-RS-OTEL-002).</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class DeliveryTraces {

    /** The MDC key of the W3C {@code traceparent}. */
    public static final String TRACEPARENT = "traceparent";

    /** The MDC key of the trace id: the 32 hex digits of the {@code traceparent}'s trace id. */
    public static final String TRACE_ID = "traceId";

    private static final Pattern WELL_FORMED = Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    private static final String ZERO_TRACE = "0".repeat(32);
    private static final String ZERO_SPAN = "0".repeat(16);

    private DeliveryTraces() {
    }

    /**
     * Tells whether a {@code traceparent} can carry a delivery: version {@code 00}, lower-case hex, neither id all
     * zeros. A delivery whose message carries none that can begins a new trace.
     *
     * @param traceparent the value, or {@code null}
     * @return whether it is well formed
     */
    public static boolean wellFormed(String traceparent) {
        return traceparent != null && WELL_FORMED.matcher(traceparent).matches()
            && !traceparent.substring(3, 35).equals(ZERO_TRACE) && !traceparent.substring(36, 52).equals(ZERO_SPAN);
    }

    /**
     * Runs a delivery with its trace on the thread's MDC, and puts back what the MDC held before, however the delivery
     * ends.
     *
     * @param trace the delivery's trace context, whose {@code traceparent} is {@linkplain #wellFormed well formed}
     * @param delivery the delivery
     * @throws IllegalArgumentException when the {@code traceparent} is not well formed
     */
    public static void within(TraceContext trace, Runnable delivery) {
        Objects.requireNonNull(trace, "trace must not be null");
        Objects.requireNonNull(delivery, "delivery must not be null");
        String traceparent = trace.traceparent();
        if (!wellFormed(traceparent)) {
            throw new IllegalArgumentException("a delivery's trace must be well formed, not " + traceparent);
        }
        String outerTraceparent = MDC.get(TRACEPARENT);
        String outerTraceId = MDC.get(TRACE_ID);
        MDC.put(TRACEPARENT, traceparent);
        MDC.put(TRACE_ID, traceparent.substring(3, 35));
        try {
            delivery.run();
        } finally {
            restore(TRACEPARENT, outerTraceparent);
            restore(TRACE_ID, outerTraceId);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
