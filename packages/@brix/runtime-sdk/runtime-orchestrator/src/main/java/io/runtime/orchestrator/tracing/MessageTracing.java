/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.tracing;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The spans of the messages the runtime sends and delivers (root BRX-RS-OTEL-002, OTEL-005; Kafka design §13 K8).
 * <p>The outbox relay and the direct bus start a PRODUCER span for each message they send, a child of the trace
 * context the message carries, and send the message with the span's context. The delivery core starts a CONSUMER
 * span for each delivery to a subscription, a child of the remote context the delivered message carries, and the
 * handler runs in it. An adapter, such as the OpenTelemetry one, records the spans; {@link #NOOP}, the default,
 * records nothing, and a message keeps the trace context it carries.</p>
 */
public interface MessageTracing {

    /** Records no span: a message keeps the trace context it carries. */
    MessageTracing NOOP = new MessageTracing() {
    };

    /**
     * Starts the PRODUCER span of a message the runtime sends, a child of the trace context the message carries, or
     * of the current context when it carries none.
     *
     * @param message the message
     * @return the span; the sent message carries its trace context
     */
    default MessageSpan producer(CanonicalOutboxMessage message) {
        return MessageSpan.unrecorded(message);
    }

    /**
     * Starts the CONSUMER span of a delivery to one subscription, a child of the remote context the delivered
     * message carries, or a new trace when it carries none that is valid.
     *
     * @param message the delivered message
     * @param subscription the subscription, as plugin id and handler id
     * @return the span; the handler runs in its trace context
     */
    default MessageSpan consumer(CanonicalOutboxMessage message, String subscription) {
        return MessageSpan.unrecorded(message);
    }

    /**
     * A started span, ended by {@link #close()}.
     */
    interface MessageSpan extends AutoCloseable {

        /**
         * Returns the W3C {@code traceparent} of the span, or of the message when no span is recorded.
         *
         * @return the traceparent, or {@code null} when there is none
         */
        String traceparent();

        /**
         * Returns the W3C {@code tracestate} that goes with {@link #traceparent()}.
         *
         * @return the tracestate, or {@code null} when there is none
         */
        String tracestate();

        /**
         * Marks the span failed.
         *
         * @param failure why
         */
        default void failed(Throwable failure) {
        }

        /** Ends the span. */
        @Override
        default void close() {
        }

        /**
         * Returns a span that records nothing and carries the message's own trace context.
         *
         * @param message the message
         * @return the span
         */
        static MessageSpan unrecorded(CanonicalOutboxMessage message) {
            String traceparent = message.traceparent();
            String tracestate = message.tracestate();
            return new MessageSpan() {
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
    }
}
