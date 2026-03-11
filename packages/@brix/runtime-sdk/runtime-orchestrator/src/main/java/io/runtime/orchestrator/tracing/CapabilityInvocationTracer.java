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
package io.runtime.orchestrator.tracing;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.tracing.CapabilityInvocation;
import io.runtime.sdk.tracing.CapabilityMetricsExporter;

/**
 * Capability Invocation Tracer.
 * 
 * <p>Core tracing component that records caller plugin, target capability, duration,
 * and other information for each capability invocation.</p>
 * 
 * <h2>Core Features</h2>
 * <ul>
 *   <li><b>Invocation Tracing</b>: Records start, end, and duration of each capability invocation</li>
 *   <li><b>Caller Identification</b>: Identifies the caller plugin ID</li>
 *   <li><b>Performance Statistics</b>: Collects invocation latency distribution</li>
 *   <li><b>Error Tracing</b>: Records error information for failed invocations</li>
 *   <li><b>Metrics Export</b>: Exports metrics via {@link CapabilityMetricsExporter}</li>
 * </ul>
 * 
 * <h2>Tracing Flow</h2>
 * <pre>
 * 1. Plugin invokes capability method
 * 2. Tracer intercepts invocation, creates TraceToken
 * 3. Records start time, caller, target capability
 * 4. Executes actual capability invocation
 * 5. Records end time, duration, result status
 * 6. Exports metrics
 * </pre>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * CapabilityInvocationTracer tracer = new CapabilityInvocationTracer();
 * tracer.setMetricsExporter(prometheusExporter);
 * 
 * // Trace capability invocation
 * TraceToken token = tracer.startInvocation("booking", HttpCapability.class, "sendRequest");
 * try {
 *     Object result = httpCapability.sendRequest(request);
 *     tracer.endSuccess(token);
 *     return result;
 * } catch (Exception e) {
 *     tracer.endFailure(token, e);
 *     throw e;
 * }
 * 
 * // Or use Lambda style
 * Object result = tracer.trace("booking", HttpCapability.class, "sendRequest", () -> {
 *     return httpCapability.sendRequest(request);
 * });
 * }</pre>
 * 
 * <h2>Architecture Notes</h2>
 * <p>This class implements Runtime Observability:
 * each Capability.invoke() records caller plugin, target capability, and duration.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class CapabilityInvocationTracer {
    
    private static final Logger logger = LoggerFactory.getLogger(CapabilityInvocationTracer.class);
    
    /**
     * Active invocation cache (lookup invocation context by TraceToken).
     */
    private final Map<String, InvocationContext> activeInvocations = new ConcurrentHashMap<>();
    
    /**
     * Metrics exporter.
     */
    private volatile CapabilityMetricsExporter metricsExporter;
    
    /**
     * Trace ID generator.
     */
    private volatile Supplier<String> traceIdGenerator = () -> UUID.randomUUID().toString();
    
    /**
     * Whether tracing is enabled.
     */
    private volatile boolean enabled = true;
    
    /**
     * Slow call threshold (milliseconds), logs warning when exceeded.
     */
    private volatile long slowCallThresholdMs = 1000;
    
    /**
     * Creates a tracer instance.
     */
    public CapabilityInvocationTracer() {
    }
    
    /**
     * Creates a tracer instance.
     * 
     * @param metricsExporter metrics exporter
     */
    public CapabilityInvocationTracer(CapabilityMetricsExporter metricsExporter) {
        this.metricsExporter = metricsExporter;
    }
    
    // ==================== Configuration Methods ====================
    
    /**
     * Sets metrics exporter.
     * 
     * @param exporter metrics exporter
     */
    public void setMetricsExporter(CapabilityMetricsExporter exporter) {
        this.metricsExporter = exporter;
    }
    
    /**
     * Sets Trace ID generator.
     * 
     * @param generator generator function
     */
    public void setTraceIdGenerator(Supplier<String> generator) {
        this.traceIdGenerator = Objects.requireNonNull(generator, "generator cannot be null");
    }
    
    /**
     * Enables or disables tracing.
     * 
     * @param enabled whether to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Sets slow call threshold.
     * 
     * @param thresholdMs threshold (milliseconds)
     */
    public void setSlowCallThresholdMs(long thresholdMs) {
        this.slowCallThresholdMs = thresholdMs;
    }
    
    // ==================== Tracing Methods ====================
    
    /**
     * Starts tracing capability invocation.
     * 
     * <p>Creates trace token, records invocation start time. Caller must call
     * {@link #endSuccess(TraceToken)} or {@link #endFailure(TraceToken, Throwable)}
     * when invocation completes.</p>
     * 
     * @param sourcePlugin caller plugin ID
     * @param capabilityType target capability type
     * @param methodName method name being invoked
     * @return trace token, used to end tracing
     */
    public TraceToken startInvocation(String sourcePlugin, Class<?> capabilityType, String methodName) {
        return startInvocation(sourcePlugin, capabilityType.getSimpleName(), methodName, null);
    }
    
    /**
     * Starts tracing capability invocation (with additional attributes).
     * 
     * @param sourcePlugin caller plugin ID
     * @param capabilityName target capability name
     * @param methodName method name being invoked
     * @param attributes additional attributes
     * @return trace token
     */
    public TraceToken startInvocation(String sourcePlugin, String capabilityName, 
                                      String methodName, Map<String, String> attributes) {
        if (!enabled) {
            return TraceToken.NOOP;
        }
        
        String traceId = traceIdGenerator.get();
        Instant startTime = Instant.now();
        
        InvocationContext context = new InvocationContext(
            traceId, sourcePlugin, capabilityName, methodName, startTime, attributes
        );
        
        activeInvocations.put(traceId, context);
        
        // Increment active call count
        if (metricsExporter != null) {
            metricsExporter.incrementActiveCall(sourcePlugin, capabilityName);
        }
        
        if (logger.isTraceEnabled()) {
            logger.trace("Started tracing capability invocation: plugin={}, capability={}, method={}, traceId={}",
                sourcePlugin, capabilityName, methodName, traceId);
        }
        
        return new TraceToken(traceId);
    }
    
    /**
     * Ends successful capability invocation.
     * 
     * @param token trace token
     */
    public void endSuccess(TraceToken token) {
        end(token, true, null, null);
    }
    
    /**
     * Ends failed capability invocation.
     * 
     * @param token trace token
     * @param error error
     */
    public void endFailure(TraceToken token, Throwable error) {
        end(token, false, 
            error != null ? error.getClass().getName() : "Unknown",
            error != null ? error.getMessage() : "Unknown error");
    }
    
    /**
     * Ends capability invocation.
     * 
     * @param token trace token
     * @param success whether successful
     * @param errorType error type
     * @param errorMessage error message
     */
    private void end(TraceToken token, boolean success, String errorType, String errorMessage) {
        if (token == null || token == TraceToken.NOOP || !enabled) {
            return;
        }
        
        InvocationContext context = activeInvocations.remove(token.getTraceId());
        if (context == null) {
            logger.warn("Trace context not found: traceId={}", token.getTraceId());
            return;
        }
        
        Instant endTime = Instant.now();
        long durationMs = Duration.between(context.getStartTime(), endTime).toMillis();
        
        // Build invocation record
        CapabilityInvocation invocation = CapabilityInvocation.builder()
            .traceId(context.getTraceId())
            .sourcePlugin(context.getSourcePlugin())
            .targetCapability(context.getCapabilityName())
            .methodName(context.getMethodName())
            .startTime(context.getStartTime())
            .endTime(endTime)
            .durationMs(durationMs)
            .success(success)
            .errorType(errorType)
            .errorMessage(errorMessage)
            .attributes(context.getAttributes())
            .build();
        
        // Export metrics
        if (metricsExporter != null) {
            metricsExporter.decrementActiveCall(context.getSourcePlugin(), context.getCapabilityName());
            metricsExporter.recordInvocation(invocation);
        }
        
        // Log
        if (success) {
            if (durationMs >= slowCallThresholdMs) {
                logger.warn("Slow capability invocation: {}, took {}ms (threshold {}ms)", 
                    invocation, durationMs, slowCallThresholdMs);
            } else if (logger.isDebugEnabled()) {
                logger.debug("Capability invocation completed: {}", invocation);
            }
        } else {
            logger.error("Capability invocation failed: {}, error: {} - {}", 
                invocation, errorType, errorMessage);
        }
    }
    
    /**
     * Traces capability invocation using Lambda style.
     * 
     * <p>Automatically handles start and end, catches exceptions and records.</p>
     * 
     * @param <T> return type
     * @param sourcePlugin caller plugin ID
     * @param capabilityType target capability type
     * @param methodName method name
     * @param callable actual invocation
     * @return invocation return value
     * @throws Exception exception thrown by invocation
     */
    public <T> T trace(String sourcePlugin, Class<?> capabilityType, 
                       String methodName, ThrowingSupplier<T> callable) throws Exception {
        TraceToken token = startInvocation(sourcePlugin, capabilityType, methodName);
        try {
            T result = callable.get();
            endSuccess(token);
            return result;
        } catch (Exception e) {
            endFailure(token, e);
            throw e;
        }
    }
    
    /**
     * Traces void capability invocation using Lambda style.
     * 
     * @param sourcePlugin caller plugin ID
     * @param capabilityType target capability type
     * @param methodName method name
     * @param runnable actual invocation
     * @throws Exception exception thrown by invocation
     */
    public void traceVoid(String sourcePlugin, Class<?> capabilityType, 
                          String methodName, ThrowingRunnable runnable) throws Exception {
        TraceToken token = startInvocation(sourcePlugin, capabilityType, methodName);
        try {
            runnable.run();
            endSuccess(token);
        } catch (Exception e) {
            endFailure(token, e);
            throw e;
        }
    }
    
    /**
     * Gets current active invocation count.
     * 
     * @return active invocation count
     */
    public int getActiveInvocationCount() {
        return activeInvocations.size();
    }
    
    /**
     * Cleans up timed out traces (for memory leak prevention).
     * 
     * @param timeoutMs timeout (milliseconds)
     * @return number of traces cleaned
     */
    public int cleanupStaleInvocations(long timeoutMs) {
        Instant threshold = Instant.now().minusMillis(timeoutMs);
        int cleaned = 0;
        
        var iterator = activeInvocations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getStartTime().isBefore(threshold)) {
                iterator.remove();
                cleaned++;
                logger.warn("Cleaned up stale trace: traceId={}, plugin={}, capability={}",
                    entry.getKey(),
                    entry.getValue().getSourcePlugin(),
                    entry.getValue().getCapabilityName());
            }
        }
        
        return cleaned;
    }
    
    // ==================== Internal Classes ====================
    
    /**
     * Trace Token.
     * 
     * <p>Used to correlate start and end calls.</p>
     */
    public static final class TraceToken {
        
        /**
         * No-op token (used when tracing is disabled).
         */
        public static final TraceToken NOOP = new TraceToken(null);
        
        private final String traceId;
        
        TraceToken(String traceId) {
            this.traceId = traceId;
        }
        
        /**
         * Gets trace ID.
         * 
         * @return trace ID
         */
        public String getTraceId() {
            return traceId;
        }
        
        /**
         * Checks if this is a no-op token.
         * 
         * @return true if no-op
         */
        public boolean isNoop() {
            return traceId == null;
        }
    }
    
    /**
     * Invocation context (internal use).
     */
    private static final class InvocationContext {
        
        private final String traceId;
        private final String sourcePlugin;
        private final String capabilityName;
        private final String methodName;
        private final Instant startTime;
        private final Map<String, String> attributes;
        
        InvocationContext(String traceId, String sourcePlugin, String capabilityName,
                         String methodName, Instant startTime, Map<String, String> attributes) {
            this.traceId = traceId;
            this.sourcePlugin = sourcePlugin;
            this.capabilityName = capabilityName;
            this.methodName = methodName;
            this.startTime = startTime;
            this.attributes = attributes;
        }
        
        String getTraceId() { return traceId; }
        String getSourcePlugin() { return sourcePlugin; }
        String getCapabilityName() { return capabilityName; }
        String getMethodName() { return methodName; }
        Instant getStartTime() { return startTime; }
        Map<String, String> getAttributes() { return attributes; }
    }
    
    /**
     * Throwing Supplier.
     * 
     * @param <T> return type
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
    
    /**
     * Throwing Runnable.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
