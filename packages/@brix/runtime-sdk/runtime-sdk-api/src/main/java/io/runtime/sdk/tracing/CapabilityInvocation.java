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

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Capability Invocation Record.
 * 
 * <p>Immutable data class recording complete information about a single capability
 * invocation, used for metrics export and tracing.</p>
 * 
 * <h2>Data Fields</h2>
 * <ul>
 *   <li>{@code traceId} - Trace ID (for correlating distributed traces)</li>
 *   <li>{@code sourcePlugin} - Caller plugin ID</li>
 *   <li>{@code targetCapability} - Target capability class name</li>
 *   <li>{@code methodName} - Invoked method name</li>
 *   <li>{@code startTime} - Invocation start time</li>
 *   <li>{@code endTime} - Invocation end time</li>
 *   <li>{@code durationMs} - Invocation duration (milliseconds)</li>
 *   <li>{@code success} - Whether successful</li>
 *   <li>{@code errorType} - Error type (on failure)</li>
 *   <li>{@code errorMessage} - Error message (on failure)</li>
 *   <li>{@code attributes} - Additional attributes</li>
 * </ul>
 * 
 * <h2>Architecture Notes</h2>
 * <p>This class is the public API definition for capability invocation data model.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public final class CapabilityInvocation {
    
    private final String traceId;
    private final String sourcePlugin;
    private final String targetCapability;
    private final String methodName;
    private final Instant startTime;
    private final Instant endTime;
    private final long durationMs;
    private final boolean success;
    private final String errorType;
    private final String errorMessage;
    private final Map<String, String> attributes;
    
    /**
     * Private constructor, use Builder to create instances.
     */
    private CapabilityInvocation(Builder builder) {
        this.traceId = Objects.requireNonNull(builder.traceId, "traceId");
        this.sourcePlugin = Objects.requireNonNull(builder.sourcePlugin, "sourcePlugin");
        this.targetCapability = Objects.requireNonNull(builder.targetCapability, "targetCapability");
        this.methodName = Objects.requireNonNull(builder.methodName, "methodName");
        this.startTime = Objects.requireNonNull(builder.startTime, "startTime");
        this.endTime = Objects.requireNonNull(builder.endTime, "endTime");
        this.durationMs = builder.durationMs;
        this.success = builder.success;
        this.errorType = builder.errorType;
        this.errorMessage = builder.errorMessage;
        this.attributes = builder.attributes.isEmpty() 
            ? Collections.emptyMap() 
            : Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }
    
    /**
     * Create Builder instance
     * 
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Get trace ID
     * 
     * @return trace ID
     */
    public String getTraceId() {
        return traceId;
    }
    
    /**
     * Get caller plugin ID
     * 
     * @return plugin ID
     */
    public String getSourcePlugin() {
        return sourcePlugin;
    }
    
    /**
     * Get target capability class name
     * 
     * @return capability class name
     */
    public String getTargetCapability() {
        return targetCapability;
    }
    
    /**
     * Get invoked method name
     * 
     * @return method name
     */
    public String getMethodName() {
        return methodName;
    }
    
    /**
     * Get invocation start time
     * 
     * @return start time
     */
    public Instant getStartTime() {
        return startTime;
    }
    
    /**
     * Get invocation end time
     * 
     * @return end time
     */
    public Instant getEndTime() {
        return endTime;
    }
    
    /**
     * Get invocation duration (milliseconds)
     * 
     * @return duration in milliseconds
     */
    public long getDurationMs() {
        return durationMs;
    }
    
    /**
     * Whether invocation succeeded
     * 
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Get error type
     * 
     * @return error type, null on success
     */
    public String getErrorType() {
        return errorType;
    }
    
    /**
     * Get error message
     * 
     * @return error message, null on success
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Get additional attributes
     * 
     * @return immutable attribute Map
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }
    
    @Override
    public String toString() {
        return String.format(
            "CapabilityInvocation{traceId=%s, plugin=%s, capability=%s, method=%s, duration=%dms, success=%s}",
            traceId, sourcePlugin, targetCapability, methodName, durationMs, success
        );
    }
    
    /**
     * CapabilityInvocation Builder
     * 
     * <h2>Usage Example</h2>
     * <pre>{@code
     * CapabilityInvocation invocation = CapabilityInvocation.builder()
     *     .traceId("trace-123")
     *     .sourcePlugin("booking")
     *     .targetCapability("HttpCapability")
     *     .methodName("sendRequest")
     *     .startTime(Instant.now().minusMillis(100))
     *     .endTime(Instant.now())
     *     .durationMs(100)
     *     .success(true)
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        
        private String traceId;
        private String sourcePlugin;
        private String targetCapability;
        private String methodName;
        private Instant startTime;
        private Instant endTime;
        private long durationMs;
        private boolean success = true;
        private String errorType;
        private String errorMessage;
        private final Map<String, String> attributes = new HashMap<>();
        
        private Builder() {}
        
        /**
         * Set trace ID
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        /**
         * Set caller plugin ID
         */
        public Builder sourcePlugin(String sourcePlugin) {
            this.sourcePlugin = sourcePlugin;
            return this;
        }
        
        /**
         * Set target capability class name
         */
        public Builder targetCapability(String targetCapability) {
            this.targetCapability = targetCapability;
            return this;
        }
        
        /**
         * Set invoked method name
         */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }
        
        /**
         * Set invocation start time
         */
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        /**
         * Set invocation end time
         */
        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        /**
         * Set invocation duration (milliseconds)
         */
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        /**
         * Set success status
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        /**
         * Set error type
         */
        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }
        
        /**
         * Set error message
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        /**
         * Add attribute
         */
        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }
        
        /**
         * Add multiple attributes
         */
        public Builder attributes(Map<String, String> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }
        
        /**
         * Build CapabilityInvocation instance
         * 
         * @return immutable CapabilityInvocation instance
         * @throws NullPointerException if required fields are null
         */
        public CapabilityInvocation build() {
            return new CapabilityInvocation(this);
        }
    }
}
