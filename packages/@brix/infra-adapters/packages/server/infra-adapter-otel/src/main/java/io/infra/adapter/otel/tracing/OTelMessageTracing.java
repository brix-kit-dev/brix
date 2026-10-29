/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.otel.tracing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.tracing.MessageTracing;

/**
 * The message spans of the runtime on OpenTelemetry (root BRX-RS-OTEL-002, OTEL-003, OTEL-004, OTEL-005; Kafka
 * design §13 K8).
 * <p>A PRODUCER span is a child of the W3C trace context the message carries, or of the current context when it
 * carries none, as a direct publish from a request does. A CONSUMER span is a child of the remote context the
 * delivered message carries; a message whose context is missing or malformed starts a new trace, because the W3C
 * propagator accepts only a valid {@code traceparent}. Each span is current while it is open, and its own context is
 * what the sent message carries or the handler runs in. The trace context always crosses the bus in W3C form,
 * whatever propagators the SDK is configured with.</p>
 * <p>Attributes follow the messaging semantic conventions 1.23.1, the version the adapter is locked to, named as the
 * schema URL of the tracer.</p>
 */
public final class OTelMessageTracing implements MessageTracing {

    /** The instrumentation scope of the spans. */
    public static final String INSTRUMENTATION_SCOPE = "io.brix.runtime.messaging";

    /** {@code messaging.system} of the runtime's event bus, whichever transport carries it. */
    public static final String MESSAGING_SYSTEM = "brix";

    static final AttributeKey<String> EVENT_ID = AttributeKey.stringKey("brix.event.id");
    static final AttributeKey<String> MESSAGE_KIND = AttributeKey.stringKey("brix.message.kind");
    static final AttributeKey<String> RELIABILITY = AttributeKey.stringKey("brix.event.reliability");
    static final AttributeKey<String> PRODUCER_PLUGIN = AttributeKey.stringKey("brix.producer.plugin_id");
    static final AttributeKey<String> SUBSCRIPTION = AttributeKey.stringKey("brix.subscription");

    private static final TextMapPropagator W3C = W3CTraceContextPropagator.getInstance();

    private static final TextMapGetter<CanonicalOutboxMessage> CARRIED = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(CanonicalOutboxMessage message) {
            return List.of("traceparent", "tracestate");
        }

        @Override
        public String get(CanonicalOutboxMessage message, String key) {
            if (message == null) {
                return null;
            }
            return switch (key) {
                case "traceparent" -> message.traceparent();
                case "tracestate" -> message.tracestate();
                default -> null;
            };
        }
    };

    private final Tracer tracer;

    /**
     * Creates the spans on an OpenTelemetry SDK.
     *
     * @param openTelemetry the SDK
     */
    public OTelMessageTracing(OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null")
            .tracerBuilder(INSTRUMENTATION_SCOPE)
            .setSchemaUrl(SemanticAttributes.SCHEMA_URL)
            .build();
    }

    @Override
    public MessageSpan producer(CanonicalOutboxMessage message) {
        Context parent = W3C.extract(Context.current(), message, CARRIED);
        return start(message, parent, SpanKind.PRODUCER, SemanticAttributes.MessagingOperationValues.PUBLISH, null);
    }

    @Override
    public MessageSpan consumer(CanonicalOutboxMessage message, String subscription) {
        Context parent = W3C.extract(Context.root(), message, CARRIED);
        return start(message, parent, SpanKind.CONSUMER, SemanticAttributes.MessagingOperationValues.PROCESS,
            subscription);
    }

    private MessageSpan start(CanonicalOutboxMessage message, Context parent, SpanKind kind, String operation,
            String subscription) {
        SpanBuilder builder = tracer.spanBuilder(message.messageType() + " " + operation)
            .setParent(parent)
            .setSpanKind(kind)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM)
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, operation)
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, message.messageType())
            .setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, message.messageId())
            .setAttribute(SemanticAttributes.MESSAGING_MESSAGE_CONVERSATION_ID, message.correlationId())
            .setAttribute(EVENT_ID, message.eventId())
            .setAttribute(MESSAGE_KIND, message.messageKind())
            .setAttribute(RELIABILITY, message.reliability())
            .setAttribute(PRODUCER_PLUGIN, message.producerPluginId());
        if (subscription != null) {
            builder.setAttribute(SUBSCRIPTION, subscription);
        }
        Span span = builder.startSpan();
        Context context = parent.with(span);
        Map<String, String> carried = new HashMap<>();
        W3C.inject(context, carried, (carrier, key, value) -> carrier.put(key, value));
        return new OTelMessageSpan(span, context.makeCurrent(), carried.get("traceparent"), carried.get("tracestate"));
    }

    private record OTelMessageSpan(Span span, Scope scope, String traceparent, String tracestate) implements MessageSpan {

        @Override
        public void failed(Throwable failure) {
            span.setStatus(StatusCode.ERROR, failure.getClass().getSimpleName());
            span.recordException(failure);
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
