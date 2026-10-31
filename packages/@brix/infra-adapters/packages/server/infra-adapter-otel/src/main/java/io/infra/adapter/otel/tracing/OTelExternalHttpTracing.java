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

import java.net.URI;
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
import io.runtime.orchestrator.external.ExternalHttpTracing;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Records the client spans of calls to external services with OpenTelemetry (root BRX-RS-OTEL-002, OTEL-005;
 * standard-link plan L8.4).
 *
 * <p>Each attempt is a CLIENT span below the call the plugin is handling, named after the method and the service. It
 * carries the method, the server's address and port, the module and the service, never the path or the query; the
 * request carries its {@code traceparent}. A 5xx answer or no answer marks it as an error.</p>
 */
public final class OTelExternalHttpTracing implements ExternalHttpTracing {

    /** The instrumentation scope of external call spans. */
    public static final String INSTRUMENTATION_SCOPE = "io.brix.runtime.external-http";

    static final AttributeKey<String> PLUGIN = AttributeKey.stringKey("brix.external.module");
    static final AttributeKey<String> SERVICE = AttributeKey.stringKey("brix.external.service");

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
     * Creates the spans.
     *
     * @param openTelemetry OpenTelemetry instance
     */
    public OTelExternalHttpTracing(OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null")
            .tracerBuilder(INSTRUMENTATION_SCOPE)
            .setSchemaUrl(SemanticAttributes.SCHEMA_URL)
            .build();
    }

    @Override
    public ExternalSpan client(String pluginId, String serviceId, String method, URI target,
            Optional<TraceContext> parent) {
        Context context = Context.current();
        if (!Span.fromContext(context).getSpanContext().isValid() && parent.isPresent()) {
            context = W3C.extract(Context.root(), parent.get(), CARRIED);
        }
        Span span = tracer.spanBuilder(method + " " + serviceId)
            .setParent(context)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, method)
            .setAttribute(SemanticAttributes.SERVER_ADDRESS, target.getHost())
            .setAttribute(SemanticAttributes.SERVER_PORT, (long) port(target))
            .setAttribute(PLUGIN, pluginId)
            .setAttribute(SERVICE, serviceId)
            .startSpan();
        Context current = context.with(span);
        Map<String, String> carried = new HashMap<>();
        W3C.inject(current, carried, (carrier, key, value) -> carrier.put(key, value));
        Optional<TraceContext> trace = Optional.ofNullable(carried.get("traceparent"))
            .map(traceparent -> new TraceContext(traceparent, carried.get("tracestate")));
        return new OTelExternalSpan(span, current.makeCurrent(), trace);
    }

    private static int port(URI target) {
        if (target.getPort() > 0) {
            return target.getPort();
        }
        return "http".equalsIgnoreCase(target.getScheme()) ? 80 : 443;
    }

    private record OTelExternalSpan(Span span, Scope scope, Optional<TraceContext> trace) implements ExternalSpan {

        @Override
        public void answered(int status) {
            span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, (long) status);
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR);
                span.setAttribute(SemanticAttributes.ERROR_TYPE, String.valueOf(status));
            }
        }

        @Override
        public void failed(String errorCode, Throwable failure) {
            span.setStatus(StatusCode.ERROR, errorCode);
            span.setAttribute(SemanticAttributes.ERROR_TYPE, errorCode);
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
