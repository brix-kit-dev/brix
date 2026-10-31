/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.otel.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.SemanticAttributes;
import io.runtime.orchestrator.query.QueryTracing.QuerySpan;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Query design §5.7 (root BRX-RS-OTEL-002, OTEL-005): a query is a CLIENT span of its caller's trace, and the
 * provider runs in a SERVER span below it.
 */
class OTelQueryTracingTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String CALLER_SPAN = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + CALLER_SPAN + "-01";

    private final InMemorySpanExporter exported = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exported))
        .build();
    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    private final OTelQueryTracing tracing = new OTelQueryTracing(sdk);

    @AfterEach
    void shutDown() {
        tracerProvider.close();
    }

    @Test
    void aQueryIsAClientSpanOfTheCallersTraceAndTheProviderAServerSpanBelowIt() {
        try (QuerySpan client = tracing.client("sample-orders", "sample-shipping", "sample-shipping.find-shipment",
                Optional.of(new TraceContext(TRACEPARENT, null)))) {
            try (QuerySpan server = tracing.server(new TraceContext(client.traceparent(), client.tracestate()),
                    "sample-shipping", "sample-shipping.find-shipment")) {
                assertThat(server.traceparent()).startsWith("00-" + TRACE_ID + "-");
            }
        }

        List<SpanData> spans = exported.getFinishedSpanItems();
        SpanData server = spans.get(0);
        SpanData client = spans.get(1);
        assertThat(client.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(client.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(client.getParentSpanId()).isEqualTo(CALLER_SPAN);
        assertThat(client.getAttributes().get(SemanticAttributes.RPC_SYSTEM)).isEqualTo("brix");
        assertThat(client.getAttributes().get(SemanticAttributes.RPC_SERVICE)).isEqualTo("sample-shipping");
        assertThat(client.getAttributes().get(SemanticAttributes.RPC_METHOD)).isEqualTo("sample-shipping.find-shipment");
        assertThat(client.getAttributes().get(OTelQueryTracing.CONSUMER_PLUGIN)).isEqualTo("sample-orders");
        assertThat(server.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(server.getParentSpanId()).isEqualTo(client.getSpanId());
        assertThat(server.getTraceId()).isEqualTo(TRACE_ID);
    }

    @Test
    void aFailedQueryIsMarkedWithItsStableCode() {
        try (QuerySpan client = tracing.client("sample-orders", "sample-shipping", "sample-shipping.find-shipment",
                Optional.empty())) {
            client.failed("query.deadline_exceeded", new IllegalStateException("late"));
        }

        SpanData client = exported.getFinishedSpanItems().get(0);
        assertThat(client.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(client.getStatus().getDescription()).isEqualTo("query.deadline_exceeded");
        assertThat(client.getAttributes().get(OTelQueryTracing.ERROR_CODE)).isEqualTo("query.deadline_exceeded");
    }

    @Test
    void aCallerWithoutAValidContextStartsANewTrace() {
        try (QuerySpan client = tracing.client("sample-orders", "sample-shipping", "sample-shipping.find-shipment",
                Optional.of(new TraceContext("not-a-traceparent", null)))) {
            assertThat(client.traceparent()).isNotBlank();
        }

        assertThat(exported.getFinishedSpanItems().get(0).getParentSpanContext().isValid()).isFalse();
    }
}
