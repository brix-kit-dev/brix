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
 * 能力调用记录
 * 
 * <p>不可变数据类，记录单次能力调用的完整信息，用于指标导出和追踪。</p>
 * 
 * <h2>数据字段</h2>
 * <ul>
 *   <li>{@code traceId} - 追踪 ID（用于关联分布式追踪）</li>
 *   <li>{@code sourcePlugin} - 调用方插件 ID</li>
 *   <li>{@code targetCapability} - 目标能力类名</li>
 *   <li>{@code methodName} - 调用的方法名</li>
 *   <li>{@code startTime} - 调用开始时间</li>
 *   <li>{@code endTime} - 调用结束时间</li>
 *   <li>{@code durationMs} - 调用耗时（毫秒）</li>
 *   <li>{@code success} - 是否成功</li>
 *   <li>{@code errorType} - 错误类型（失败时）</li>
 *   <li>{@code errorMessage} - 错误消息（失败时）</li>
 *   <li>{@code attributes} - 附加属性</li>
 * </ul>
 * 
 * <h2>架构说明</h2>
 * <p>本类是 v3.0 架构蓝图 4.4-1 任务数据模型的公共 API 定义。</p>
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
     * 私有构造函数，使用 Builder 创建实例
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
     * 创建 Builder 实例
     * 
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 获取追踪 ID
     * 
     * @return 追踪 ID
     */
    public String getTraceId() {
        return traceId;
    }
    
    /**
     * 获取调用方插件 ID
     * 
     * @return 插件 ID
     */
    public String getSourcePlugin() {
        return sourcePlugin;
    }
    
    /**
     * 获取目标能力类名
     * 
     * @return 能力类名
     */
    public String getTargetCapability() {
        return targetCapability;
    }
    
    /**
     * 获取调用的方法名
     * 
     * @return 方法名
     */
    public String getMethodName() {
        return methodName;
    }
    
    /**
     * 获取调用开始时间
     * 
     * @return 开始时间
     */
    public Instant getStartTime() {
        return startTime;
    }
    
    /**
     * 获取调用结束时间
     * 
     * @return 结束时间
     */
    public Instant getEndTime() {
        return endTime;
    }
    
    /**
     * 获取调用耗时（毫秒）
     * 
     * @return 耗时毫秒数
     */
    public long getDurationMs() {
        return durationMs;
    }
    
    /**
     * 是否调用成功
     * 
     * @return 成功返回 true
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 获取错误类型
     * 
     * @return 错误类型，成功时为 null
     */
    public String getErrorType() {
        return errorType;
    }
    
    /**
     * 获取错误消息
     * 
     * @return 错误消息，成功时为 null
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 获取附加属性
     * 
     * @return 不可变的属性 Map
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
     * CapabilityInvocation 构建器
     * 
     * <h2>使用示例</h2>
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
         * 设置追踪 ID
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        /**
         * 设置调用方插件 ID
         */
        public Builder sourcePlugin(String sourcePlugin) {
            this.sourcePlugin = sourcePlugin;
            return this;
        }
        
        /**
         * 设置目标能力类名
         */
        public Builder targetCapability(String targetCapability) {
            this.targetCapability = targetCapability;
            return this;
        }
        
        /**
         * 设置调用的方法名
         */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }
        
        /**
         * 设置调用开始时间
         */
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        /**
         * 设置调用结束时间
         */
        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        /**
         * 设置调用耗时（毫秒）
         */
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        /**
         * 设置是否成功
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        /**
         * 设置错误类型
         */
        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }
        
        /**
         * 设置错误消息
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        /**
         * 添加属性
         */
        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }
        
        /**
         * 添加多个属性
         */
        public Builder attributes(Map<String, String> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }
        
        /**
         * 构建 CapabilityInvocation 实例
         * 
         * @return 不可变的 CapabilityInvocation 实例
         * @throws NullPointerException 如果必填字段为空
         */
        public CapabilityInvocation build() {
            return new CapabilityInvocation(this);
        }
    }
}
