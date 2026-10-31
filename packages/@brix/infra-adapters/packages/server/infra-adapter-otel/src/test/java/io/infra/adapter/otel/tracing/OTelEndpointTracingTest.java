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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.SemanticAttributes;
import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.EndpointTracing;
import io.runtime.orchestrator.endpoint.EndpointTracing.EndpointSpan;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * The server spans of HTTP endpoint calls (root BRX-RS-OTEL-002, OTEL-004, OTEL-005; standard-link plan L7.6).
 */
class OTelEndpointTracingTest {

    private static final String CALLER_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String CALLER_SPAN = "00f067aa0ba902b7";
    private static final String CALLER = "00-" + CALLER_TRACE + "-" + CALLER_SPAN + "-01";

    private final InMemorySpanExporter exported = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exported))
        .build();
    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    private final OTelEndpointTracing tracing = new OTelEndpointTracing(sdk);

    @AfterEach
    void shutDown() {
        tracerProvider.close();
    }

    @Test
    void aCallIsAServerSpanInANewTraceLinkedToTheCallersAndCurrentWhileItRuns() {
        String own;
        try (EndpointSpan span = tracing.server(route(), EndpointTracing.caller(CALLER))) {
            own = span.trace().traceparent();
            assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(own.substring(3, 35));
            span.finished(201, null);
        }

        SpanData server = exported.getFinishedSpanItems().get(0);
        assertThat(server.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(server.getName()).isEqualTo("GET /api/sample-orders/orders/{orderId}");
        assertThat(server.getParentSpanContext().isValid()).as("an untrusted caller is no parent").isFalse();
        assertThat(server.getTraceId()).isNotEqualTo(CALLER_TRACE).isEqualTo(own.substring(3, 35));
        assertThat(server.getLinks()).hasSize(1);
        assertThat(server.getLinks().get(0).getSpanContext().getTraceId()).isEqualTo(CALLER_TRACE);
        assertThat(server.getLinks().get(0).getSpanContext().getSpanId()).isEqualTo(CALLER_SPAN);
        assertThat(server.getAttributes().get(SemanticAttributes.HTTP_REQUEST_METHOD)).isEqualTo("GET");
        assertThat(server.getAttributes().get(SemanticAttributes.HTTP_ROUTE))
            .isEqualTo("/api/sample-orders/orders/{orderId}");
        assertThat(server.getAttributes().get(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE)).isEqualTo(201L);
        assertThat(server.getAttributes().get(OTelEndpointTracing.PLUGIN)).isEqualTo("sample-orders");
        assertThat(server.getAttributes().get(OTelEndpointTracing.ENDPOINT)).isEqualTo("sample-orders.show.v1");
        assertThat(server.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(Span.current().getSpanContext().isValid()).as("the scope is closed").isFalse();
    }

    @Test
    void aCallWithoutAWellFormedTraceparentHasNoLink() {
        try (EndpointSpan span = tracing.server(route(), EndpointTracing.caller("00-" + CALLER_TRACE.toUpperCase()
                + "-" + CALLER_SPAN + "-01"))) {
            span.finished(200, null);
        }

        assertThat(exported.getFinishedSpanItems().get(0).getLinks()).isEmpty();
    }

    @Test
    void aServerFailureMarksTheSpanAndAClientFailureDoesNot() {
        try (EndpointSpan span = tracing.server(route(), Optional.empty())) {
            span.finished(503, "endpoint.rejected");
        }
        try (EndpointSpan span = tracing.server(route(), Optional.empty())) {
            span.finished(404, "sample-orders.order_not_found");
        }

        List<SpanData> spans = exported.getFinishedSpanItems();
        assertThat(spans.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(spans.get(0).getAttributes().get(SemanticAttributes.ERROR_TYPE)).isEqualTo("endpoint.rejected");
        assertThat(spans.get(1).getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(spans.get(1).getAttributes().get(SemanticAttributes.ERROR_TYPE))
            .isEqualTo("sample-orders.order_not_found");
    }

    private static EndpointRoute route() {
        return new EndpointRoute("sample-orders", "sample-orders.show.v1", "GET", "/api/sample-orders/orders/{orderId}",
            EndpointAccessPolicy.fromDeclaration(Map.of("mode", "anonymous"), Map.of("policyRef", "sample-open"),
                Map.of("mode", "forbidden")),
            (EndpointHandler<EndpointInvocation<Object>, Object>) invocation -> null);
    }
}
