/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.runtime.sdk.capability.LogLevel;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.SpanContext;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * OpenTelemetry-based observability capability implementation.
 * 
 * <p>Provides complete OpenTelemetry integration with support for distributed tracing,
 * metrics collection, and structured logging.</p>
 * 
 * <h2>Core Features</h2>
 * <ul>
 *   <li><b>Tracing</b>: Based on OpenTelemetry Tracer, supports Span creation and context propagation</li>
 *   <li><b>Metrics</b>: Based on OpenTelemetry Meter, supports Counter, Gauge, Histogram</li>
 *   <li><b>Logging</b>: Integrated with SLF4J, automatically correlates Trace ID</li>
 * </ul>
 * 
 * <h2>Metric Types</h2>
 * <table border="1">
 *   <tr><th>Type</th><th>Description</th><th>Example</th></tr>
 *   <tr><td>Counter</td><td>Cumulative counter</td><td>Total requests, error count</td></tr>
 *   <tr><td>Histogram</td><td>Distribution statistics</td><td>Response time, request size</td></tr>
 * </table>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * OTelObservabilityCapability observability = new OTelObservabilityCapability(
 *     openTelemetry, "my-service"
 * );
 * 
 * // Log a message
 * observability.info("Processing request: {}", requestId);
 * 
 * // Record metrics
 * observability.recordMetric("api.requests", 1, Map.of("endpoint", "/users"));
 * 
 * // Trace an operation
 * try (var span = observability.startSpan("process-order")) {
 *     // Business logic
 *     observability.addSpanAttribute("order.id", orderId);
 * }
 * }</pre>
 * 
 * <h2>Architecture Notes</h2>
 * <p>This class implements the ObservabilityCapability interface defined in Layer 1,
 * belonging to Layer 2 Adapter layer.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 * @see ObservabilityCapability
 */
@Capability(
    type = ObservabilityCapability.class,
    name = "otel-observability",
    description = "OpenTelemetry-based observability capability implementation",
    level = CapabilityLevel.CORE,
    aliases = {"observability", "otelObservability"}
)
public class OTelObservabilityCapability implements ObservabilityCapability, AutoCloseable {
    
    /**
     * Default service name.
     */
    private static final String DEFAULT_SERVICE_NAME = "brix-service";
    
    /**
     * Metrics namespace prefix.
     */
    private static final String METRICS_PREFIX = "brix.";
    
    /**
     * SLF4J Logger.
     */
    private final Logger logger;
    
    /**
     * OpenTelemetry instance.
     */
    private final OpenTelemetry openTelemetry;
    
    /**
     * OpenTelemetry Tracer.
     */
    private final Tracer tracer;
    
    /**
     * OpenTelemetry Meter.
     */
    private final Meter meter;
    
    /**
     * Service name.
     */
    private final String serviceName;
    
    /**
     * Counter cache.
     */
    private final Map<String, LongCounter> counterCache = new ConcurrentHashMap<>();
    
    /**
     * Histogram cache.
     */
    private final Map<String, DoubleHistogram> histogramCache = new ConcurrentHashMap<>();
    
    /**
     * Creates OTelObservabilityCapability instance.
     *
     * @param openTelemetry OpenTelemetry instance
     * @param serviceName Service name
     */
    public OTelObservabilityCapability(OpenTelemetry openTelemetry, String serviceName) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "OpenTelemetry cannot be null");
        this.serviceName = serviceName != null ? serviceName : DEFAULT_SERVICE_NAME;
        this.logger = LoggerFactory.getLogger(this.serviceName);
        this.tracer = openTelemetry.getTracer(this.serviceName);
        this.meter = openTelemetry.getMeter(this.serviceName);
    }
    
    /**
     * Creates OTelObservabilityCapability instance (using default service name).
     *
     * @param openTelemetry OpenTelemetry instance
     */
    public OTelObservabilityCapability(OpenTelemetry openTelemetry) {
        this(openTelemetry, DEFAULT_SERVICE_NAME);
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Records logs using SLF4J, automatically correlating Trace ID in MDC.</p>
     */
    @Override
    public void log(LogLevel level, String message, Object... args) {
        // Get current Span's Trace ID for log correlation
        Span currentSpan = Span.current();
        String traceId = currentSpan.getSpanContext().getTraceId();
        
        // Add Trace ID to MDC
        org.slf4j.MDC.put("traceId", traceId);
        
        try {
            switch (level) {
                case TRACE:
                    logger.trace(message, args);
                    break;
                case DEBUG:
                    logger.debug(message, args);
                    break;
                case INFO:
                    logger.info(message, args);
                    break;
                case WARN:
                    logger.warn(message, args);
                    break;
                case ERROR:
                    logger.error(message, args);
                    break;
            }
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Automatically selects Counter or Histogram based on metric value characteristics:</p>
     * <ul>
     *   <li>Integer value of 1: use Counter for accumulation</li>
     *   <li>Other cases: use Histogram for distribution recording</li>
     * </ul>
     */
    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        String fullName = name.startsWith(METRICS_PREFIX) ? name : METRICS_PREFIX + name;
        Attributes attributes = buildAttributes(tags);
        
        // If value is 1, use Counter; otherwise use Histogram
        if (value == 1.0) {
            LongCounter counter = counterCache.computeIfAbsent(fullName, n -> 
                    meter.counterBuilder(n)
                            .setDescription("Counter for " + n)
                            .build()
            );
            counter.add(1, attributes);
        } else {
            DoubleHistogram histogram = histogramCache.computeIfAbsent(fullName, n ->
                    meter.histogramBuilder(n)
                            .setDescription("Histogram for " + n)
                            .build()
            );
            histogram.record(value, attributes);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public SpanContext currentSpan() {
        Span span = Span.current();
        io.opentelemetry.api.trace.SpanContext otelContext = span.getSpanContext();
        
        if (!otelContext.isValid()) {
            return SpanContext.empty();
        }
        
        return new SpanContext(
                otelContext.getTraceId(),
                otelContext.getSpanId(),
                null, // OpenTelemetry SpanContext does not directly provide parent span id
                otelContext.isSampled()
        );
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addSpanAttribute(String key, String value) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setAttribute(key, value);
        }
    }
    
    /**
     * Starts a new Span.
     *
     * @param operationName Operation name
     * @return AutoCloseable Span wrapper
     */
    public SpanScope startSpan(String operationName) {
        return startSpan(operationName, SpanKind.INTERNAL);
    }
    
    /**
     * Starts a new Span with specified type.
     *
     * @param operationName Operation name
     * @param kind Span type
     * @return AutoCloseable Span wrapper
     */
    public SpanScope startSpan(String operationName, SpanKind kind) {
        Span span = tracer.spanBuilder(operationName)
                .setSpanKind(kind)
                .startSpan();
        Scope scope = span.makeCurrent();
        return new SpanScope(span, scope);
    }
    
    /**
     * Executes operation within Span context.
     *
     * @param <T> Return type
     * @param operationName Operation name
     * @param operation Operation to execute
     * @return Operation result
     */
    public <T> T executeInSpan(String operationName, Supplier<T> operation) {
        try (SpanScope spanScope = startSpan(operationName)) {
            try {
                return operation.get();
            } catch (Exception e) {
                spanScope.recordException(e);
                throw e;
            }
        }
    }
    
    /**
     * Executes operation within Span context (no return value).
     *
     * @param operationName Operation name
     * @param operation Operation to execute
     */
    public void executeInSpan(String operationName, Runnable operation) {
        executeInSpan(operationName, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Records Counter metric.
     *
     * @param name Metric name
     * @param delta Increment value
     * @param tags Tags
     */
    public void incrementCounter(String name, long delta, Map<String, String> tags) {
        String fullName = name.startsWith(METRICS_PREFIX) ? name : METRICS_PREFIX + name;
        Attributes attributes = buildAttributes(tags);
        
        LongCounter counter = counterCache.computeIfAbsent(fullName, n ->
                meter.counterBuilder(n)
                        .setDescription("Counter for " + n)
                        .build()
        );
        counter.add(delta, attributes);
    }
    
    /**
     * Records Histogram metric.
     *
     * @param name Metric name
     * @param value Value
     * @param tags Tags
     */
    public void recordHistogram(String name, double value, Map<String, String> tags) {
        String fullName = name.startsWith(METRICS_PREFIX) ? name : METRICS_PREFIX + name;
        Attributes attributes = buildAttributes(tags);
        
        DoubleHistogram histogram = histogramCache.computeIfAbsent(fullName, n ->
                meter.histogramBuilder(n)
                        .setDescription("Histogram for " + n)
                        .build()
        );
        histogram.record(value, attributes);
    }
    
    /**
     * Builds OpenTelemetry Attributes.
     */
    private Attributes buildAttributes(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Attributes.empty();
        }
        
        AttributesBuilder builder = Attributes.builder();
        tags.forEach((key, value) -> builder.put(AttributeKey.stringKey(key), value));
        return builder.build();
    }
    
    /**
     * Gets OpenTelemetry instance.
     *
     * @return OpenTelemetry instance
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }
    
    /**
     * Gets Tracer.
     *
     * @return Tracer instance
     */
    public Tracer getTracer() {
        return tracer;
    }
    
    /**
     * Gets Meter.
     *
     * @return Meter instance
     */
    public Meter getMeter() {
        return meter;
    }
    
    /**
     * Gets service name.
     *
     * @return Service name
     */
    public String getServiceName() {
        return serviceName;
    }
    
    @Override
    public void close() {
        // OpenTelemetry SDK shutdown is managed externally
        counterCache.clear();
        histogramCache.clear();
    }
    
    /**
     * Span scope wrapper.
     * 
     * <p>Implements AutoCloseable for try-with-resources syntax support.</p>
     */
    public static final class SpanScope implements AutoCloseable {
        
        private final Span span;
        private final Scope scope;
        
        SpanScope(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }
        
        /**
         * Adds Span attribute.
         *
         * @param key Attribute key
         * @param value Attribute value
         * @return this
         */
        public SpanScope setAttribute(String key, String value) {
            span.setAttribute(key, value);
            return this;
        }
        
        /**
         * Adds Span attribute (numeric value).
         *
         * @param key Attribute key
         * @param value Attribute value
         * @return this
         */
        public SpanScope setAttribute(String key, long value) {
            span.setAttribute(key, value);
            return this;
        }
        
        /**
         * Records exception.
         *
         * @param exception Exception
         */
        public void recordException(Throwable exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
        }
        
        /**
         * Sets status.
         *
         * @param status Status code
         * @param description Description
         */
        public void setStatus(StatusCode status, String description) {
            span.setStatus(status, description);
        }
        
        /**
         * Gets underlying Span.
         *
         * @return Span instance
         */
        public Span getSpan() {
            return span;
        }
        
        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
