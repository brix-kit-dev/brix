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
package io.infra.adapter.otel.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.runtime.sdk.tracing.CapabilityInvocation;
import io.runtime.sdk.tracing.CapabilityMetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * OpenTelemetry-based capability invocation metrics exporter.
 * 
 * <p>Exports capability invocation metrics to OpenTelemetry, supporting Prometheus format export.</p>
 * 
 * <h2>Exported Metrics</h2>
 * <table border="1">
 *   <tr><th>Metric Name</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>brix_capability_call_total</td><td>Counter</td><td>Total capability invocations</td></tr>
 *   <tr><td>brix_capability_call_latency_seconds</td><td>Histogram</td><td>Capability invocation latency distribution</td></tr>
 *   <tr><td>brix_capability_active_calls</td><td>UpDownCounter</td><td>Current active invocations</td></tr>
 *   <tr><td>brix_eventbus_direct_bypass_total</td><td>Counter</td><td>Event bus bypass count</td></tr>
 *   <tr><td>brix_architecture_violations_runtime</td><td>Counter</td><td>Runtime architecture violations</td></tr>
 * </table>
 * 
 * <h2>Labels</h2>
 * <ul>
 *   <li>{@code plugin} - Plugin ID</li>
 *   <li>{@code capability} - Capability name</li>
 *   <li>{@code method} - Method name</li>
 *   <li>{@code status} - Invocation status (success/error)</li>
 *   <li>{@code error_type} - Error type (on failure)</li>
 * </ul>
 * 
 * <h2>Architecture Notes</h2>
 * <p>This class implements v3.0 architecture blueprint task 4.4-4:
 * Add architecture compliance metrics export in infra-adapter-otel (Prometheus format).</p>
 *
 * @author Brix Team
 * @since 3.0.0
 * @see CapabilityMetricsExporter
 */
public class OTelCapabilityMetricsExporter implements CapabilityMetricsExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(OTelCapabilityMetricsExporter.class);
    
    /**
     * Service name (used for metric labels).
     */
    private static final String SERVICE_NAME = "brix-runtime";
    
    // ==================== Label Keys ====================
    
    private static final AttributeKey<String> PLUGIN_KEY = AttributeKey.stringKey("plugin");
    private static final AttributeKey<String> CAPABILITY_KEY = AttributeKey.stringKey("capability");
    private static final AttributeKey<String> METHOD_KEY = AttributeKey.stringKey("method");
    private static final AttributeKey<String> STATUS_KEY = AttributeKey.stringKey("status");
    private static final AttributeKey<String> ERROR_TYPE_KEY = AttributeKey.stringKey("error_type");
    private static final AttributeKey<String> VIOLATION_TYPE_KEY = AttributeKey.stringKey("violation_type");
    private static final AttributeKey<String> EVENT_TYPE_KEY = AttributeKey.stringKey("event_type");
    private static final AttributeKey<String> SOURCE_PLUGIN_KEY = AttributeKey.stringKey("source_plugin");
    private static final AttributeKey<String> DETAILS_KEY = AttributeKey.stringKey("details");
    
    // ==================== Metric Instances ====================
    
    /**
     * Capability invocation total counter.
     */
    private final LongCounter callTotalCounter;
    
    /**
     * Capability invocation latency histogram.
     */
    private final DoubleHistogram callLatencyHistogram;
    
    /**
     * Current active invocations counter.
     */
    private final LongUpDownCounter activeCallsCounter;
    
    /**
     * Event bus bypass counter.
     */
    private final LongCounter eventBusBypassCounter;
    
    /**
     * Architecture violation counter.
     */
    private final LongCounter architectureViolationsCounter;
    
    /**
     * Creates OTel capability metrics exporter.
     * 
     * @param openTelemetry OpenTelemetry instance
     */
    public OTelCapabilityMetricsExporter(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry cannot be null");
        
        Meter meter = openTelemetry.getMeter(SERVICE_NAME);
        
        // Initialize capability invocation total counter
        this.callTotalCounter = meter.counterBuilder(CAPABILITY_CALL_TOTAL)
            .setDescription("Total capability invocations")
            .setUnit("calls")
            .build();
        
        // Initialize capability invocation latency histogram
        this.callLatencyHistogram = meter.histogramBuilder(CAPABILITY_CALL_LATENCY)
            .setDescription("Capability invocation latency distribution (seconds)")
            .setUnit("seconds")
            .setExplicitBucketBoundariesAdvice(java.util.Arrays.asList(
                0.001, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
            ))
            .build();
        
        // Initialize current active invocations counter
        this.activeCallsCounter = meter.upDownCounterBuilder(CAPABILITY_ACTIVE_CALLS)
            .setDescription("Current active capability invocations")
            .setUnit("calls")
            .build();
        
        // Initialize event bus bypass counter
        this.eventBusBypassCounter = meter.counterBuilder(EVENTBUS_BYPASS_TOTAL)
            .setDescription("Direct calls bypassing event bus (should be 0)")
            .setUnit("calls")
            .build();
        
        // Initialize architecture violation counter
        this.architectureViolationsCounter = meter.counterBuilder(ARCHITECTURE_VIOLATIONS)
            .setDescription("Runtime architecture violations")
            .setUnit("violations")
            .build();
        
        logger.info("OTel capability metrics exporter initialized");
    }
    
    @Override
    public void recordInvocation(CapabilityInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation cannot be null");
        
        String status = invocation.isSuccess() ? "success" : "error";
        
        // Build attributes
        AttributesBuilder attrsBuilder = Attributes.builder()
            .put(PLUGIN_KEY, invocation.getSourcePlugin())
            .put(CAPABILITY_KEY, invocation.getTargetCapability())
            .put(METHOD_KEY, invocation.getMethodName())
            .put(STATUS_KEY, status);
        
        if (!invocation.isSuccess() && invocation.getErrorType() != null) {
            attrsBuilder.put(ERROR_TYPE_KEY, invocation.getErrorType());
        }
        
        Attributes attrs = attrsBuilder.build();
        
        // Record invocation count
        callTotalCounter.add(1, attrs);
        
        // Record invocation latency (convert to seconds)
        double latencySeconds = invocation.getDurationMs() / 1000.0;
        callLatencyHistogram.record(latencySeconds, attrs);
        
        if (logger.isTraceEnabled()) {
            logger.trace("Recorded capability invocation metric: plugin={}, capability={}, method={}, status={}, latency={}ms",
                invocation.getSourcePlugin(),
                invocation.getTargetCapability(),
                invocation.getMethodName(),
                status,
                invocation.getDurationMs());
        }
    }
    
    @Override
    public void incrementActiveCall(String pluginId, String capabilityName) {
        Attributes attrs = Attributes.builder()
            .put(PLUGIN_KEY, pluginId)
            .put(CAPABILITY_KEY, capabilityName)
            .build();
        
        activeCallsCounter.add(1, attrs);
    }
    
    @Override
    public void decrementActiveCall(String pluginId, String capabilityName) {
        Attributes attrs = Attributes.builder()
            .put(PLUGIN_KEY, pluginId)
            .put(CAPABILITY_KEY, capabilityName)
            .build();
        
        activeCallsCounter.add(-1, attrs);
    }
    
    @Override
    public void recordEventBusBypass(String eventType, String sourcePlugin) {
        Attributes attrs = Attributes.builder()
            .put(EVENT_TYPE_KEY, eventType)
            .put(SOURCE_PLUGIN_KEY, sourcePlugin)
            .build();
        
        eventBusBypassCounter.add(1, attrs);
        
        logger.warn("Detected event bus bypass: eventType={}, sourcePlugin={}",
            eventType, sourcePlugin);
    }
    
    @Override
    public void recordArchitectureViolation(String violationType, String details) {
        Attributes attrs = Attributes.builder()
            .put(VIOLATION_TYPE_KEY, violationType)
            .put(DETAILS_KEY, details)
            .build();
        
        architectureViolationsCounter.add(1, attrs);
        
        logger.warn("Detected runtime architecture violation: type={}, details={}",
            violationType, details);
    }
    
    @Override
    public void close() {
        logger.info("OTel capability metrics exporter closed");
        // OpenTelemetry SDK handles resource cleanup automatically
    }
}
