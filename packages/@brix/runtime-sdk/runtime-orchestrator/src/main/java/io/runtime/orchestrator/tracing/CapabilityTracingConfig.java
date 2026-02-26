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
 * 能力调用追踪配置
 * 
 * <p>配置追踪器的行为参数。</p>
 * 
 * <h2>配置项</h2>
 * <ul>
 *   <li>{@code enabled} - 是否启用追踪（默认 true）</li>
 *   <li>{@code slowCallThresholdMs} - 慢调用阈值（默认 1000ms）</li>
 *   <li>{@code staleCleanupIntervalMs} - 超时追踪清理间隔（默认 60000ms）</li>
 *   <li>{@code staleTimeoutMs} - 追踪超时时间（默认 300000ms，5分钟）</li>
 *   <li>{@code sampleRate} - 采样率（默认 1.0，即 100%）</li>
 *   <li>{@code exportMetrics} - 是否导出指标（默认 true）</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * CapabilityTracingConfig config = CapabilityTracingConfig.builder()
 *     .enabled(true)
 *     .slowCallThresholdMs(500)
 *     .sampleRate(0.1)  // 10% 采样
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
     * 默认配置
     */
    public static final CapabilityTracingConfig DEFAULT = builder().build();
    
    /**
     * 是否启用追踪
     */
    private final boolean enabled;
    
    /**
     * 慢调用阈值（毫秒）
     */
    private final long slowCallThresholdMs;
    
    /**
     * 超时追踪清理间隔（毫秒）
     */
    private final long staleCleanupIntervalMs;
    
    /**
     * 追踪超时时间（毫秒）
     */
    private final long staleTimeoutMs;
    
    /**
     * 采样率（0.0 - 1.0）
     */
    private final double sampleRate;
    
    /**
     * 是否导出指标
     */
    private final boolean exportMetrics;
    
    /**
     * 是否记录方法参数
     */
    private final boolean logMethodArguments;
    
    /**
     * 是否记录返回值
     */
    private final boolean logReturnValue;
    
    /**
     * 私有构造函数
     * 
     * @param builder Builder 实例
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
     * 创建 Builder
     * 
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // ==================== Getters ====================
    
    /**
     * 检查是否启用追踪
     * 
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 获取慢调用阈值
     * 
     * @return 阈值（毫秒）
     */
    public long getSlowCallThresholdMs() {
        return slowCallThresholdMs;
    }
    
    /**
     * 获取超时追踪清理间隔
     * 
     * @return 间隔（毫秒）
     */
    public long getStaleCleanupIntervalMs() {
        return staleCleanupIntervalMs;
    }
    
    /**
     * 获取追踪超时时间
     * 
     * @return 超时时间（毫秒）
     */
    public long getStaleTimeoutMs() {
        return staleTimeoutMs;
    }
    
    /**
     * 获取采样率
     * 
     * @return 采样率（0.0 - 1.0）
     */
    public double getSampleRate() {
        return sampleRate;
    }
    
    /**
     * 检查是否导出指标
     * 
     * @return 导出返回 true
     */
    public boolean isExportMetrics() {
        return exportMetrics;
    }
    
    /**
     * 检查是否记录方法参数
     * 
     * @return 记录返回 true
     */
    public boolean isLogMethodArguments() {
        return logMethodArguments;
    }
    
    /**
     * 检查是否记录返回值
     * 
     * @return 记录返回 true
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
     * 配置构建器
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
         * 设置是否启用追踪
         * 
         * @param enabled 是否启用
         * @return this
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        /**
         * 设置慢调用阈值
         * 
         * @param thresholdMs 阈值（毫秒）
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
         * 设置超时追踪清理间隔
         * 
         * @param intervalMs 间隔（毫秒）
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
         * 设置追踪超时时间
         * 
         * @param timeoutMs 超时时间（毫秒）
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
         * 设置采样率
         * 
         * @param rate 采样率（0.0 - 1.0）
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
         * 设置是否导出指标
         * 
         * @param export 是否导出
         * @return this
         */
        public Builder exportMetrics(boolean export) {
            this.exportMetrics = export;
            return this;
        }
        
        /**
         * 设置是否记录方法参数
         * 
         * @param log 是否记录
         * @return this
         */
        public Builder logMethodArguments(boolean log) {
            this.logMethodArguments = log;
            return this;
        }
        
        /**
         * 设置是否记录返回值
         * 
         * @param log 是否记录
         * @return this
         */
        public Builder logReturnValue(boolean log) {
            this.logReturnValue = log;
            return this;
        }
        
        /**
         * 构建配置实例
         * 
         * @return 配置实例
         */
        public CapabilityTracingConfig build() {
            return new CapabilityTracingConfig(this);
        }
    }
}
