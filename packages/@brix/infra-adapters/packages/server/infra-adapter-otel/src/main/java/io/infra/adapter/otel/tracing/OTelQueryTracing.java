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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import io.runtime.orchestrator.query.QueryTracing;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Records the spans of typed queries with OpenTelemetry (root BRX-RS-OTEL-002, OTEL-005; query design §5.7).
 *
 * <p>The caller's query is a CLIENT span, a child of the current context or, when there is none, of the trace context
 * the caller's entry carried; the provider runs in a SERVER span, a child of the CLIENT span. Attributes follow the
 * RPC conventions of the locked semantic conventions: {@code rpc.system=brix}, {@code rpc.service} is the providing
 * plugin and {@code rpc.method} the query type. Neither span carries a request or result value.</p>
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
public final class OTelQueryTracing implements QueryTracing {

    /** The instrumentation scope of the spans. */
    public static final String INSTRUMENTATION_SCOPE = "io.brix.runtime.query";

    /** {@code rpc.system} of typed queries, whichever transport carries them. */
    public static final String RPC_SYSTEM = "brix";

    static final AttributeKey<String> CONSUMER_PLUGIN = AttributeKey.stringKey("brix.query.consumer");
    static final AttributeKey<String> ERROR_CODE = AttributeKey.stringKey("brix.query.error_code");

    private static final TextMapPropagator W3C = W3CTraceContextPropagator.getInstance();

    private static final TextMapGetter<TraceContext> CARRIED = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(TraceContext trace) {
            return List.of("traceparent", "tracestate");
        }

        @Override
        public String get(TraceContext trace, String key) {
            if (trace == null) {
                return null;
            }
            return switch (key) {
                case "traceparent" -> trace.traceparent();
                case "tracestate" -> trace.tracestate();
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
    public OTelQueryTracing(OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null")
            .tracerBuilder(INSTRUMENTATION_SCOPE)
            .setSchemaUrl(SemanticAttributes.SCHEMA_URL)
            .build();
    }

    @Override
    public QuerySpan client(String consumerPluginId, String providerPluginId, String queryType,
            Optional<TraceContext> parent) {
        Context context = Context.current();
        if (!Span.fromContext(context).getSpanContext().isValid() && parent.isPresent()) {
            context = W3C.extract(Context.root(), parent.get(), CARRIED);
        }
        Span span = tracer.spanBuilder(providerPluginId + "/" + queryType)
            .setParent(context)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, RPC_SYSTEM)
            .setAttribute(SemanticAttributes.RPC_SERVICE, providerPluginId)
            .setAttribute(SemanticAttributes.RPC_METHOD, queryType)
            .setAttribute(CONSUMER_PLUGIN, consumerPluginId)
            .startSpan();
        return started(span, context.with(span));
    }

    @Override
    public QuerySpan server(TraceContext client, String providerPluginId, String queryType) {
        Context context = W3C.extract(Context.root(), client, CARRIED);
        Span span = tracer.spanBuilder(providerPluginId + "/" + queryType)
            .setParent(context)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, RPC_SYSTEM)
            .setAttribute(SemanticAttributes.RPC_SERVICE, providerPluginId)
            .setAttribute(SemanticAttributes.RPC_METHOD, queryType)
            .startSpan();
        return started(span, context.with(span));
    }

    private static QuerySpan started(Span span, Context context) {
        Map<String, String> carried = new HashMap<>();
        W3C.inject(context, carried, (carrier, key, value) -> carrier.put(key, value));
        return new OTelQuerySpan(span, context.makeCurrent(), carried.get("traceparent"), carried.get("tracestate"));
    }

    private record OTelQuerySpan(Span span, Scope scope, String traceparent, String tracestate) implements QuerySpan {

        @Override
        public void failed(String errorCode, Throwable failure) {
            span.setStatus(StatusCode.ERROR, errorCode);
            span.setAttribute(ERROR_CODE, errorCode);
            if (failure != null) {
                span.recordException(failure);
            }
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
