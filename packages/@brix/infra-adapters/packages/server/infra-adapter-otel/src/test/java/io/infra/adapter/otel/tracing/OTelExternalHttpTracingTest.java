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

import java.net.URI;
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
import io.runtime.orchestrator.external.ExternalHttpTracing.ExternalSpan;
import io.runtime.sdk.plugin.TraceContext;

/**
 * The client spans of external calls (root BRX-RS-OTEL-002, OTEL-005; standard-link plan L8.4).
 */
class OTelExternalHttpTracingTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String CALLER_SPAN = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + CALLER_SPAN + "-01";

    private final InMemorySpanExporter exported = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exported))
        .build();
    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    private final OTelExternalHttpTracing tracing = new OTelExternalHttpTracing(sdk);

    @AfterEach
    void shutDown() {
        tracerProvider.close();
    }

    @Test
    void anAttemptIsAClientSpanBelowTheCallThePluginHandlesAndTheRequestCarriesIt() {
        String carried;
        try (ExternalSpan span = tracing.client("platform-auth", "google-oauth-jwks", "GET",
                URI.create("https://www.googleapis.com"), Optional.of(new TraceContext(TRACEPARENT, null)))) {
            carried = span.trace().orElseThrow().traceparent();
            span.answered(200);
        }

        SpanData client = exported.getFinishedSpanItems().get(0);
        assertThat(client.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(client.getName()).isEqualTo("GET google-oauth-jwks");
        assertThat(client.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(client.getParentSpanId()).isEqualTo(CALLER_SPAN);
        assertThat(carried).isEqualTo("00-" + TRACE_ID + "-" + client.getSpanId() + "-01");
        assertThat(client.getAttributes().get(SemanticAttributes.SERVER_ADDRESS)).isEqualTo("www.googleapis.com");
        assertThat(client.getAttributes().get(SemanticAttributes.SERVER_PORT)).isEqualTo(443L);
        assertThat(client.getAttributes().get(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
        assertThat(client.getAttributes().get(OTelExternalHttpTracing.PLUGIN)).isEqualTo("platform-auth");
        assertThat(client.getAttributes().get(OTelExternalHttpTracing.SERVICE)).isEqualTo("google-oauth-jwks");
        assertThat(client.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    void aServerErrorOrNoAnswerMarksTheSpan() {
        try (ExternalSpan span = tracing.client("plugin-a", "billing", "POST", URI.create("http://billing.test:8080"),
                Optional.empty())) {
            span.answered(503);
        }
        try (ExternalSpan span = tracing.client("plugin-a", "billing", "GET", URI.create("https://billing.test"),
                Optional.empty())) {
            span.failed("external_http.deadline_exceeded", null);
        }

        assertThat(exported.getFinishedSpanItems().get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(exported.getFinishedSpanItems().get(0).getAttributes().get(SemanticAttributes.SERVER_PORT))
            .isEqualTo(8080L);
        assertThat(exported.getFinishedSpanItems().get(1).getAttributes().get(SemanticAttributes.ERROR_TYPE))
            .isEqualTo("external_http.deadline_exceeded");
    }
}
