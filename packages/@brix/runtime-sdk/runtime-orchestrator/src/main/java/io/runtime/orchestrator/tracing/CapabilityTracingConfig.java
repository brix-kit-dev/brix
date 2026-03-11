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

import java.util.Objects;

/**
 * Capability Invocation Tracing Configuration.
 * 
 * <p>Configures tracer behavior parameters.</p>
 * 
 * <h2>Configuration Options</h2>
 * <ul>
 *   <li>{@code enabled} - Whether to enable tracing (default: true)</li>
 *   <li>{@code slowCallThresholdMs} - Slow call threshold (default: 1000ms)</li>
 *   <li>{@code staleCleanupIntervalMs} - Stale trace cleanup interval (default: 60000ms)</li>
 *   <li>{@code staleTimeoutMs} - Trace timeout duration (default: 300000ms, 5 minutes)</li>
 *   <li>{@code sampleRate} - Sampling rate (default: 1.0, i.e., 100%)</li>
 *   <li>{@code exportMetrics} - Whether to export metrics (default: true)</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * CapabilityTracingConfig config = CapabilityTracingConfig.builder()
 *     .enabled(true)
 *     .slowCallThresholdMs(500)
 *     .sampleRate(0.1)  // 10% sampling
 *     .build();
 * 
 * CapabilityInvocationTracer tracer = new CapabilityInvocationTracer();
 * tracer.configure(config);
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public final class CapabilityTracingConfig {
    
    /**
     * Default configuration.
     */
    public static final CapabilityTracingConfig DEFAULT = builder().build();
    
    /**
     * Whether tracing is enabled.
     */
    private final boolean enabled;
    
    /**
     * Slow call threshold (milliseconds).
     */
    private final long slowCallThresholdMs;
    
    /**
     * Stale trace cleanup interval (milliseconds).
     */
    private final long staleCleanupIntervalMs;
    
    /**
     * Trace timeout duration (milliseconds).
     */
    private final long staleTimeoutMs;
    
    /**
     * Sampling rate (0.0 - 1.0).
     */
    private final double sampleRate;
    
    /**
     * Whether to export metrics.
     */
    private final boolean exportMetrics;
    
    /**
     * Whether to log method arguments.
     */
    private final boolean logMethodArguments;
    
    /**
     * Whether to log return values.
     */
    private final boolean logReturnValue;
    
    /**
     * Private constructor.
     * 
     * @param builder Builder instance
     */
    private CapabilityTracingConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.slowCallThresholdMs = builder.slowCallThresholdMs;
        this.staleCleanupIntervalMs = builder.staleCleanupIntervalMs;
        this.staleTimeoutMs = builder.staleTimeoutMs;
        this.sampleRate = builder.sampleRate;
        this.exportMetrics = builder.exportMetrics;
        this.logMethodArguments = builder.logMethodArguments;
        this.logReturnValue = builder.logReturnValue;
    }
    
    /**
     * Creates a Builder.
     * 
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // ==================== Getters ====================
    
    /**
     * Checks if tracing is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets slow call threshold.
     * 
     * @return threshold (milliseconds)
     */
    public long getSlowCallThresholdMs() {
        return slowCallThresholdMs;
    }
    
    /**
     * Gets stale trace cleanup interval.
     * 
     * @return interval (milliseconds)
     */
    public long getStaleCleanupIntervalMs() {
        return staleCleanupIntervalMs;
    }
    
    /**
     * Gets trace timeout duration.
     * 
     * @return timeout duration (milliseconds)
     */
    public long getStaleTimeoutMs() {
        return staleTimeoutMs;
    }
    
    /**
     * Gets sampling rate.
     * 
     * @return sampling rate (0.0 - 1.0)
     */
    public double getSampleRate() {
        return sampleRate;
    }
    
    /**
     * Checks if metrics export is enabled.
     * 
     * @return true if exporting
     */
    public boolean isExportMetrics() {
        return exportMetrics;
    }
    
    /**
     * Checks if method arguments logging is enabled.
     * 
     * @return true if logging
     */
    public boolean isLogMethodArguments() {
        return logMethodArguments;
    }
    
    /**
     * Checks if return value logging is enabled.
     * 
     * @return true if logging
     */
    public boolean isLogReturnValue() {
        return logReturnValue;
    }
    
    @Override
    public String toString() {
        return String.format(
            "CapabilityTracingConfig{enabled=%s, slowCallThreshold=%dms, sampleRate=%.2f, exportMetrics=%s}",
            enabled, slowCallThresholdMs, sampleRate, exportMetrics
        );
    }
    
    // ==================== Builder ====================
    
    /**
     * Configuration Builder.
     */
    public static final class Builder {
        
        private boolean enabled = true;
        private long slowCallThresholdMs = 1000;
        private long staleCleanupIntervalMs = 60000;
        private long staleTimeoutMs = 300000;
        private double sampleRate = 1.0;
        private boolean exportMetrics = true;
        private boolean logMethodArguments = false;
        private boolean logReturnValue = false;
        
        private Builder() {
        }
        
        /**
         * Sets whether to enable tracing.
         * 
         * @param enabled whether to enable
         * @return this
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        /**
         * Sets slow call threshold.
         * 
         * @param thresholdMs threshold (milliseconds)
         * @return this
         */
        public Builder slowCallThresholdMs(long thresholdMs) {
            if (thresholdMs <= 0) {
                throw new IllegalArgumentException("slowCallThresholdMs must be positive");
            }
            this.slowCallThresholdMs = thresholdMs;
            return this;
        }
        
        /**
         * Sets stale trace cleanup interval.
         * 
         * @param intervalMs interval (milliseconds)
         * @return this
         */
        public Builder staleCleanupIntervalMs(long intervalMs) {
            if (intervalMs <= 0) {
                throw new IllegalArgumentException("staleCleanupIntervalMs must be positive");
            }
            this.staleCleanupIntervalMs = intervalMs;
            return this;
        }
        
        /**
         * Sets trace timeout duration.
         * 
         * @param timeoutMs timeout duration (milliseconds)
         * @return this
         */
        public Builder staleTimeoutMs(long timeoutMs) {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("staleTimeoutMs must be positive");
            }
            this.staleTimeoutMs = timeoutMs;
            return this;
        }
        
        /**
         * Sets sampling rate.
         * 
         * @param rate sampling rate (0.0 - 1.0)
         * @return this
         */
        public Builder sampleRate(double rate) {
            if (rate < 0.0 || rate > 1.0) {
                throw new IllegalArgumentException("sampleRate must be between 0.0 and 1.0");
            }
            this.sampleRate = rate;
            return this;
        }
        
        /**
         * Sets whether to export metrics.
         * 
         * @param export whether to export
         * @return this
         */
        public Builder exportMetrics(boolean export) {
            this.exportMetrics = export;
            return this;
        }
        
        /**
         * Sets whether to log method arguments.
         * 
         * @param log whether to log
         * @return this
         */
        public Builder logMethodArguments(boolean log) {
            this.logMethodArguments = log;
            return this;
        }
        
        /**
         * Sets whether to log return values.
         * 
         * @param log whether to log
         * @return this
         */
        public Builder logReturnValue(boolean log) {
            this.logReturnValue = log;
            return this;
        }
        
        /**
         * Builds the configuration instance.
         * 
         * @return configuration instance
         */
        public CapabilityTracingConfig build() {
            return new CapabilityTracingConfig(this);
        }
    }
}
