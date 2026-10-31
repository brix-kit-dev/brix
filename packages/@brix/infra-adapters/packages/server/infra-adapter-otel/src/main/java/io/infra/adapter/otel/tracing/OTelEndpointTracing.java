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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.EndpointTracing;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Records the server spans of HTTP endpoint calls with OpenTelemetry (root BRX-RS-MANIFEST-022, OTEL-002, OTEL-004,
 * OTEL-005; standard-link plan L7.6).
 *
 * <p>Each call is a SERVER span with no parent: an HTTP caller is an untrusted upstream. A caller's well-formed
 * {@code traceparent} becomes a link. The span is named {@code {method} {route}} after the route template, and carries
 * the method, the route, the module and the endpoint, then the status; 5xx answers mark it as an error.</p>
 */
public final class OTelEndpointTracing implements EndpointTracing {

    /** The instrumentation scope of endpoint spans. */
    public static final String INSTRUMENTATION_SCOPE = "io.brix.runtime.endpoint";

    static final AttributeKey<String> PLUGIN = AttributeKey.stringKey("brix.endpoint.module");
    static final AttributeKey<String> ENDPOINT = AttributeKey.stringKey("brix.endpoint.id");

    private static final TextMapPropagator W3C = W3CTraceContextPropagator.getInstance();

    private static final TextMapGetter<TraceContext> CARRIED = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(TraceContext trace) {
            return List.of("traceparent");
        }

        @Override
        public String get(TraceContext trace, String key) {
            return trace != null && "traceparent".equals(key) ? trace.traceparent() : null;
        }
    };

    private final Tracer tracer;

    /**
     * Creates the spans.
     *
     * @param openTelemetry OpenTelemetry instance
     */
    public OTelEndpointTracing(OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null")
            .tracerBuilder(INSTRUMENTATION_SCOPE)
            .setSchemaUrl(SemanticAttributes.SCHEMA_URL)
            .build();
    }

    @Override
    public EndpointSpan server(EndpointRoute route, Optional<TraceContext> caller) {
        var builder = tracer.spanBuilder(route.method() + " " + route.path())
            .setNoParent()
            .setSpanKind(SpanKind.SERVER)
            .setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, route.method())
            .setAttribute(SemanticAttributes.HTTP_ROUTE, route.path())
            .setAttribute(PLUGIN, route.pluginId())
            .setAttribute(ENDPOINT, route.endpointId());
        caller.map(trace -> Span.fromContext(W3C.extract(Context.root(), trace, CARRIED)).getSpanContext())
            .filter(SpanContext::isValid)
            .ifPresent(builder::addLink);
        Span span = builder.startSpan();
        Context context = Context.root().with(span);
        Map<String, String> carried = new HashMap<>();
        W3C.inject(context, carried, (carrier, key, value) -> carrier.put(key, value));
        String traceparent = carried.get("traceparent");
        TraceContext trace = traceparent != null
            ? new TraceContext(traceparent, carried.get("tracestate"))
            : EndpointTracing.Traceparents.random();
        return new OTelEndpointSpan(span, context.makeCurrent(), trace);
    }

    private record OTelEndpointSpan(Span span, Scope scope, TraceContext trace) implements EndpointSpan {

        @Override
        public void finished(int status, String errorCode) {
            span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, (long) status);
            if (errorCode != null) {
                span.setAttribute(SemanticAttributes.ERROR_TYPE, errorCode);
            }
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR, errorCode);
            }
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
