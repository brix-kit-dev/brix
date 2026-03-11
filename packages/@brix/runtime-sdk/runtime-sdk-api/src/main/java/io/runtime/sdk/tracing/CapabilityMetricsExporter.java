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
package io.runtime.sdk.tracing;

/**
 * Capability Invocation Metrics Exporter Interface.
 * 
 * <p>Defines the export contract for capability invocation metrics,
 * supporting different backend implementations (Prometheus, OTel, etc.).</p>
 * 
 * <h2>Metric Types</h2>
 * <ul>
 *   <li><b>Counter</b>: Invocation count, success/failure count</li>
 *   <li><b>Histogram</b>: Invocation latency distribution</li>
 *   <li><b>Gauge</b>: Current active invocation count</li>
 * </ul>
 * 
 * <h2>Standard Metric Names</h2>
 * <pre>
 * brix_capability_call_total{plugin="booking", capability="HttpCapability", method="sendRequest", status="success"}
 * brix_capability_call_latency_seconds{plugin="booking", capability="HttpCapability", method="sendRequest"}
 * brix_capability_active_calls{plugin="booking", capability="HttpCapability"}
 * brix_eventbus_direct_bypass_total
 * brix_architecture_violations_runtime{type="direct_capability_call"}
 * </pre>
 * 
 * <h2>Implementation Requirements</h2>
 * <ol>
 *   <li>Exporter must be thread-safe</li>
 *   <li>Export failures should not block business invocations</li>
 *   <li>Metric naming follows Prometheus naming conventions</li>
 * </ol>
 * 
 * <h2>Architecture Notes</h2>
 * <p>This interface is the SPI definition for Runtime Observability.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface CapabilityMetricsExporter {
    
    /**
     * Metric prefix.
     */
    String METRIC_PREFIX = "brix_";
    
    /**
     * Capability invocation total count metric name.
     */
    String CAPABILITY_CALL_TOTAL = METRIC_PREFIX + "capability_call_total";
    
    /**
     * Capability invocation latency metric name (Histogram).
     */
    String CAPABILITY_CALL_LATENCY = METRIC_PREFIX + "capability_call_latency_seconds";
    
    /**
     * Current active invocation count metric name.
     */
    String CAPABILITY_ACTIVE_CALLS = METRIC_PREFIX + "capability_active_calls";
    
    /**
     * Event bus bypass count metric name.
     */
    String EVENTBUS_BYPASS_TOTAL = METRIC_PREFIX + "eventbus_direct_bypass_total";
    
    /**
     * Runtime architecture violation metric name.
     */
    String ARCHITECTURE_VIOLATIONS = METRIC_PREFIX + "architecture_violations_runtime";
    
    /**
     * Record capability invocation
     * 
     * <p>Records metric data for a single capability invocation, including:</p>
     * <ul>
     *   <li>Invocation count (Counter)</li>
     *   <li>Invocation latency (Histogram)</li>
     *   <li>Success/Failure status</li>
     * </ul>
     * 
     * @param invocation capability invocation record
     */
    void recordInvocation(CapabilityInvocation invocation);
    
    /**
     * Increment active call count
     * 
     * <p>Called when capability invocation starts, used to track current concurrent calls.</p>
     * 
     * @param pluginId plugin ID
     * @param capabilityName capability name
     */
    void incrementActiveCall(String pluginId, String capabilityName);
    
    /**
     * Decrement active call count
     * 
     * <p>Called when capability invocation ends (regardless of success or failure).</p>
     * 
     * @param pluginId plugin ID
     * @param capabilityName capability name
     */
    void decrementActiveCall(String pluginId, String capabilityName);
    
    /**
     * Record event bus bypass
     * 
     * <p>Records when a call that bypasses the event bus directly is detected.</p>
     * 
     * @param eventType event type
     * @param sourcePlugin source plugin ID
     */
    void recordEventBusBypass(String eventType, String sourcePlugin);
    
    /**
     * Record architecture violation
     * 
     * <p>Records architecture violations detected at runtime.</p>
     * 
     * @param violationType violation type
     * @param details violation details
     */
    void recordArchitectureViolation(String violationType, String details);
    
    /**
     * Close exporter
     * 
     * <p>Release resources, flush unsent metrics.</p>
     */
    default void close() {
        // Default empty implementation
    }
}
