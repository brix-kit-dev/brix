/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.otel.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.SemanticAttributes;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.tracing.MessageTracing.MessageSpan;

/**
 * The adapter's contract for the runtime's message spans: context inject and extract, PRODUCER and CONSUMER spans,
 * error status and the locked semantic conventions (root BRX-RS-OTEL-002 to OTEL-005; Kafka design §13 K8).
 */
class OTelMessageTracingTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PUBLISHER_SPAN = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + PUBLISHER_SPAN + "-01";

    private final InMemorySpanExporter exported = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exported))
        .build();
    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    private final OTelMessageTracing tracing = new OTelMessageTracing(sdk);

    @AfterEach
    void shutDown() {
        tracerProvider.close();
    }

    @Test
    void aProducerSpanIsAChildOfTheMessagesContextAndTheMessageIsSentWithItsOwn() {
        String sent;
        try (MessageSpan producer = tracing.producer(message(TRACEPARENT, "vendor=1"))) {
            sent = producer.traceparent();
            assertThat(producer.tracestate()).isEqualTo("vendor=1");
        }

        SpanData span = exported.getFinishedSpanItems().get(0);
        assertThat(span.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(span.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(span.getParentSpanId()).isEqualTo(PUBLISHER_SPAN);
        assertThat(span.getParentSpanContext().isRemote()).isTrue();
        assertThat(sent).isEqualTo("00-" + TRACE_ID + "-" + span.getSpanId() + "-01");
        assertThat(span.getName()).isEqualTo("MemberAccepted publish");
        assertThat(span.getAttributes().get(SemanticAttributes.MESSAGING_SYSTEM)).isEqualTo("brix");
        assertThat(span.getAttributes().get(SemanticAttributes.MESSAGING_OPERATION)).isEqualTo("publish");
        assertThat(span.getAttributes().get(SemanticAttributes.MESSAGING_DESTINATION_NAME)).isEqualTo("MemberAccepted");
        assertThat(span.getAttributes().get(SemanticAttributes.MESSAGING_MESSAGE_ID)).isEqualTo("message-1");
        assertThat(span.getAttributes().get(OTelMessageTracing.EVENT_ID)).isEqualTo("event-1");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    void aConsumerSpanIsAChildOfTheProducerSpanItsMessageCarries() {
        String sent;
        try (MessageSpan producer = tracing.producer(message(TRACEPARENT, null))) {
            sent = producer.traceparent();
        }
        String handled;
        try (MessageSpan consumer = tracing.consumer(message(sent, null), "subscriber.projection.v1")) {
            handled = consumer.traceparent();
        }

        List<SpanData> spans = exported.getFinishedSpanItems();
        SpanData producer = spans.get(0);
        SpanData consumer = spans.get(1);
        assertThat(consumer.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(consumer.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(consumer.getParentSpanId()).isEqualTo(producer.getSpanId());
        assertThat(consumer.getParentSpanContext().isRemote()).isTrue();
        assertThat(handled).isEqualTo("00-" + TRACE_ID + "-" + consumer.getSpanId() + "-01");
        assertThat(consumer.getName()).isEqualTo("MemberAccepted process");
        assertThat(consumer.getAttributes().get(SemanticAttributes.MESSAGING_OPERATION)).isEqualTo("process");
        assertThat(consumer.getAttributes().get(OTelMessageTracing.SUBSCRIPTION)).isEqualTo("subscriber.projection.v1");
    }

    @Test
    void theHandlerRunsWithItsConsumerSpanCurrent() {
        try (MessageSpan consumer = tracing.consumer(message(TRACEPARENT, null), "subscriber.projection.v1")) {
            assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(TRACE_ID);
            assertThat(consumer.traceparent()).contains(Span.current().getSpanContext().getSpanId());
        }
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @Test
    void aFailedDeliveryMarksItsConsumerSpanAsAnError() {
        try (MessageSpan consumer = tracing.consumer(message(TRACEPARENT, null), "subscriber.projection.v1")) {
            consumer.failed(new IllegalStateException("projection unavailable"));
        }

        SpanData span = exported.getFinishedSpanItems().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("IllegalStateException");
        assertThat(span.getEvents()).anyMatch(event -> event.getName().equals("exception"));
    }

    @Test
    void aMalformedContextStartsANewTraceInsteadOfBeingContinued() {
        // Root BRX-RS-OTEL-004: a trace context that does not validate is not inherited.
        String handled;
        try (MessageSpan consumer = tracing.consumer(message("00-not-a-trace-01", null), "subscriber.projection.v1")) {
            handled = consumer.traceparent();
        }

        SpanData span = exported.getFinishedSpanItems().get(0);
        assertThat(span.getParentSpanContext().isValid()).isFalse();
        // A new trace id may carry the W3C random flag next to the sampled one.
        assertThat(handled).startsWith("00-" + span.getTraceId() + "-" + span.getSpanId() + "-");
    }

    @Test
    void aMessageWithoutContextIsSentInAChildOfTheCurrentSpan() {
        // The direct bus publishes from a request: its PRODUCER span continues the request's span.
        Span request = sdk.getTracer("test").spanBuilder("request").startSpan();
        try (Scope current = request.makeCurrent(); MessageSpan producer = tracing.producer(message(null, null))) {
            assertThat(producer.traceparent()).contains(request.getSpanContext().getTraceId());
        } finally {
            request.end();
        }

        SpanData producer = exported.getFinishedSpanItems().get(0);
        assertThat(producer.getParentSpanId()).isEqualTo(request.getSpanContext().getSpanId());
        assertThat(producer.getParentSpanContext().isRemote()).isFalse();
    }

    @Test
    void theSpansNameTheSemanticConventionsTheAdapterIsLockedTo() {
        // Root BRX-RS-OTEL-003.
        try (MessageSpan producer = tracing.producer(message(TRACEPARENT, null))) {
            assertThat(producer.traceparent()).isNotNull();
        }

        assertThat(exported.getFinishedSpanItems().get(0).getInstrumentationScopeInfo().getSchemaUrl())
            .isEqualTo("https://opentelemetry.io/schemas/1.23.1");
    }

    private static CanonicalOutboxMessage message(String traceparent, String tracestate) {
        return new CanonicalOutboxMessage("message-1", "event-1", "EVENT", "MemberAccepted", "1.0.0", "CRITICAL",
            "conformance-publisher", "TENANT", 910001L, "member-1", OffsetDateTime.now(ZoneOffset.UTC), "correlation-1",
            null, traceparent, tracestate, "{}", 0);
    }
}
