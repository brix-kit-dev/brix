/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.util.Map;

import io.runtime.sdk.annotation.Since;

/**
 * Observability Capability Contract
 * 
 * <p>Provides a unified abstract interface for logging, metrics, and tracing,
 * serving as the core capability for cloud-native observability.
 * Modules record runtime information through this interface without knowing
 * the underlying implementation (OpenTelemetry/Prometheus/Jaeger).</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li><b>Logging</b>: Structured log recording</li>
 *   <li><b>Metrics</b>: Business and technical metrics collection</li>
 *   <li><b>Tracing</b>: Distributed trace collection</li>
 * </ul>
 * 
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Low Intrusiveness</b>: Simple API that doesn't affect business code</li>
 *   <li><b>High Performance</b>: Async collection that doesn't block main flow</li>
 *   <li><b>Standards Compliant</b>: Based on OpenTelemetry standards</li>
 * </ul>
 * 
 * <h3>Log Levels</h3>
 * <p>Uses {@link LogLevel} enum, from low to high: TRACE &lt; DEBUG &lt; INFO &lt; WARN &lt; ERROR</p>
 * 
 * <h3>Metric Types</h3>
 * <ul>
 *   <li><b>Counter</b>: Cumulative counter, e.g., total request count</li>
 *   <li><b>Gauge</b>: Instant value, e.g., current connection count</li>
 *   <li><b>Histogram</b>: Distribution statistics, e.g., response time</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private ObservabilityCapability observability;
 * 
 * public void processOrder(Order order) {
 *     // Record log
 *     observability.log(LogLevel.INFO, "Processing order: {}", order.getId());
 *     
 *     // Record metrics
 *     observability.recordMetric("orders.processed", 1, 
 *         Map.of("type", order.getType(), "region", order.getRegion()));
 *     
 *     // Add tracing attributes
 *     observability.addSpanAttribute("order.id", order.getId());
 *     observability.addSpanAttribute("order.amount", String.valueOf(order.getAmount()));
 * }
 * }</pre>
 * 
 * <h3>Implementation Notes</h3>
 * <ul>
 *   <li>Full Product Host: OpenTelemetry + Prometheus + Jaeger</li>
 *   <li>Embedded Host: SLF4J logging + basic metrics</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Since("3.0.0")
public interface ObservabilityCapability {

    /**
     * Record log
     * 
     * <p>Supports SLF4J-style {} placeholders with automatic parameter substitution</p>
     * 
     * @param level   log level
     * @param message log message template
     * @param args    message arguments, last argument if Throwable will be recorded as exception
     */
    void log(LogLevel level, String message, Object... args);

    /**
     * Record metric
     * 
     * <p>Metric naming convention: {module}.{category}.{name}, e.g., "booking.api.requests"</p>
     * 
     * @param name  metric name, using dot-separated namespace
     * @param value metric value
     * @param tags  tags for multi-dimensional aggregation
     */
    void recordMetric(String name, double value, Map<String, String> tags);

    /**
     * Get current Span context
     * 
     * <p>Used for passing trace information across services</p>
     * 
     * @return current Span context, returns empty context if not in trace context
     */
    SpanContext currentSpan();

    /**
     * Add Span attribute
     * 
     * <p>Adds business attributes to current trace Span for trace analysis</p>
     * 
     * @param key   attribute key
     * @param value attribute value
     */
    void addSpanAttribute(String key, String value);

    /**
     * Record INFO level log (convenience method)
     * 
     * @param message log message
     * @param args    message arguments
     */
    default void info(String message, Object... args) {
        log(LogLevel.INFO, message, args);
    }

    /**
     * Record WARN level log (convenience method)
     * 
     * @param message log message
     * @param args    message arguments
     */
    default void warn(String message, Object... args) {
        log(LogLevel.WARN, message, args);
    }

    /**
     * Record ERROR level log (convenience method)
     * 
     * @param message log message
     * @param args    message arguments
     */
    default void error(String message, Object... args) {
        log(LogLevel.ERROR, message, args);
    }

    /**
     * Record DEBUG level log (convenience method)
     * 
     * @param message log message
     * @param args    message arguments
     */
    default void debug(String message, Object... args) {
        log(LogLevel.DEBUG, message, args);
    }

    /**
     * Record metric without tags (convenience method)
     * 
     * @param name  metric name
     * @param value metric value
     */
    default void recordMetric(String name, double value) {
        recordMetric(name, value, Map.of());
    }

    /**
     * Increment counter (convenience method)
     * 
     * @param name counter name
     * @param tags tags
     */
    default void incrementCounter(String name, Map<String, String> tags) {
        recordMetric(name, 1.0, tags);
    }
}
