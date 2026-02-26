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

import io.runtime.sdk.tracing.CapabilityInvocation;
import io.runtime.sdk.tracing.CapabilityMetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 能力调用追踪器
 * 
 * <p>核心追踪组件，记录每次能力调用的调用方插件、目标能力、耗时等信息。</p>
 * 
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>调用追踪</b>：记录每次能力调用的开始、结束、耗时</li>
 *   <li><b>调用者识别</b>：识别调用方插件 ID</li>
 *   <li><b>性能统计</b>：统计调用延迟分布</li>
 *   <li><b>错误追踪</b>：记录调用失败的错误信息</li>
 *   <li><b>指标导出</b>：通过 {@link CapabilityMetricsExporter} 导出指标</li>
 * </ul>
 * 
 * <h2>追踪流程</h2>
 * <pre>
 * 1. 插件调用能力方法
 * 2. 追踪器拦截调用，创建 TraceToken
 * 3. 记录开始时间、调用方、目标能力
 * 4. 执行实际能力调用
 * 5. 记录结束时间、耗时、结果状态
 * 6. 导出指标
 * </pre>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * CapabilityInvocationTracer tracer = new CapabilityInvocationTracer();
 * tracer.setMetricsExporter(prometheusExporter);
 * 
 * // 追踪能力调用
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
 * // 或使用 Lambda 风格
 * Object result = tracer.trace("booking", HttpCapability.class, "sendRequest", () -> {
 *     return httpCapability.sendRequest(request);
 * });
 * }</pre>
 * 
 * <h2>架构说明</h2>
 * <p>本类实现 v3.0 架构蓝图 4.4-1 任务：
 * 每次 Capability.invoke() 记录调用方插件、目标能力、耗时。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class CapabilityInvocationTracer {
    
    private static final Logger logger = LoggerFactory.getLogger(CapabilityInvocationTracer.class);
    
    /**
     * 活跃调用缓存（根据 TraceToken 查找调用上下文）
     */
    private final Map<String, InvocationContext> activeInvocations = new ConcurrentHashMap<>();
    
    /**
     * 指标导出器
     */
    private volatile CapabilityMetricsExporter metricsExporter;
    
    /**
     * Trace ID 生成器
     */
    private volatile Supplier<String> traceIdGenerator = () -> UUID.randomUUID().toString();
    
    /**
     * 是否启用追踪
     */
    private volatile boolean enabled = true;
    
    /**
     * 慢调用阈值（毫秒），超过此值记录警告日志
     */
    private volatile long slowCallThresholdMs = 1000;
    
    /**
     * 创建追踪器实例
     */
    public CapabilityInvocationTracer() {
    }
    
    /**
     * 创建追踪器实例
     * 
     * @param metricsExporter 指标导出器
     */
    public CapabilityInvocationTracer(CapabilityMetricsExporter metricsExporter) {
        this.metricsExporter = metricsExporter;
    }
    
    // ==================== 配置方法 ====================
    
    /**
     * 设置指标导出器
     * 
     * @param exporter 指标导出器
     */
    public void setMetricsExporter(CapabilityMetricsExporter exporter) {
        this.metricsExporter = exporter;
    }
    
    /**
     * 设置 Trace ID 生成器
     * 
     * @param generator 生成器函数
     */
    public void setTraceIdGenerator(Supplier<String> generator) {
        this.traceIdGenerator = Objects.requireNonNull(generator, "generator cannot be null");
    }
    
    /**
     * 启用或禁用追踪
     * 
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 设置慢调用阈值
     * 
     * @param thresholdMs 阈值（毫秒）
     */
    public void setSlowCallThresholdMs(long thresholdMs) {
        this.slowCallThresholdMs = thresholdMs;
    }
    
    // ==================== 追踪方法 ====================
    
    /**
     * 开始追踪能力调用
     * 
     * <p>创建追踪令牌，记录调用开始时间。调用方必须在调用结束后
     * 调用 {@link #endSuccess(TraceToken)} 或 {@link #endFailure(TraceToken, Throwable)}。</p>
     * 
     * @param sourcePlugin 调用方插件 ID
     * @param capabilityType 目标能力类型
     * @param methodName 调用的方法名
     * @return 追踪令牌，用于结束追踪
     */
    public TraceToken startInvocation(String sourcePlugin, Class<?> capabilityType, String methodName) {
        return startInvocation(sourcePlugin, capabilityType.getSimpleName(), methodName, null);
    }
    
    /**
     * 开始追踪能力调用（带附加属性）
     * 
     * @param sourcePlugin 调用方插件 ID
     * @param capabilityName 目标能力名称
     * @param methodName 调用的方法名
     * @param attributes 附加属性
     * @return 追踪令牌
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
        
        // 增加活跃调用计数
        if (metricsExporter != null) {
            metricsExporter.incrementActiveCall(sourcePlugin, capabilityName);
        }
        
        if (logger.isTraceEnabled()) {
            logger.trace("开始追踪能力调用: plugin={}, capability={}, method={}, traceId={}",
                sourcePlugin, capabilityName, methodName, traceId);
        }
        
        return new TraceToken(traceId);
    }
    
    /**
     * 结束成功的能力调用
     * 
     * @param token 追踪令牌
     */
    public void endSuccess(TraceToken token) {
        end(token, true, null, null);
    }
    
    /**
     * 结束失败的能力调用
     * 
     * @param token 追踪令牌
     * @param error 错误
     */
    public void endFailure(TraceToken token, Throwable error) {
        end(token, false, 
            error != null ? error.getClass().getName() : "Unknown",
            error != null ? error.getMessage() : "Unknown error");
    }
    
    /**
     * 结束能力调用
     * 
     * @param token 追踪令牌
     * @param success 是否成功
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     */
    private void end(TraceToken token, boolean success, String errorType, String errorMessage) {
        if (token == null || token == TraceToken.NOOP || !enabled) {
            return;
        }
        
        InvocationContext context = activeInvocations.remove(token.getTraceId());
        if (context == null) {
            logger.warn("未找到追踪上下文: traceId={}", token.getTraceId());
            return;
        }
        
        Instant endTime = Instant.now();
        long durationMs = Duration.between(context.getStartTime(), endTime).toMillis();
        
        // 构建调用记录
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
        
        // 导出指标
        if (metricsExporter != null) {
            metricsExporter.decrementActiveCall(context.getSourcePlugin(), context.getCapabilityName());
            metricsExporter.recordInvocation(invocation);
        }
        
        // 记录日志
        if (success) {
            if (durationMs >= slowCallThresholdMs) {
                logger.warn("慢能力调用: {}, 耗时 {}ms（阈值 {}ms）", 
                    invocation, durationMs, slowCallThresholdMs);
            } else if (logger.isDebugEnabled()) {
                logger.debug("能力调用完成: {}", invocation);
            }
        } else {
            logger.error("能力调用失败: {}, 错误: {} - {}", 
                invocation, errorType, errorMessage);
        }
    }
    
    /**
     * 使用 Lambda 风格追踪能力调用
     * 
     * <p>自动处理开始和结束，捕获异常并记录。</p>
     * 
     * @param <T> 返回值类型
     * @param sourcePlugin 调用方插件 ID
     * @param capabilityType 目标能力类型
     * @param methodName 方法名
     * @param callable 实际调用
     * @return 调用返回值
     * @throws Exception 调用抛出的异常
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
     * 使用 Lambda 风格追踪无返回值的能力调用
     * 
     * @param sourcePlugin 调用方插件 ID
     * @param capabilityType 目标能力类型
     * @param methodName 方法名
     * @param runnable 实际调用
     * @throws Exception 调用抛出的异常
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
     * 获取当前活跃调用数
     * 
     * @return 活跃调用数
     */
    public int getActiveInvocationCount() {
        return activeInvocations.size();
    }
    
    /**
     * 清理超时的追踪（用于防止内存泄漏）
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return 清理的追踪数量
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
                logger.warn("清理超时追踪: traceId={}, plugin={}, capability={}",
                    entry.getKey(),
                    entry.getValue().getSourcePlugin(),
                    entry.getValue().getCapabilityName());
            }
        }
        
        return cleaned;
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 追踪令牌
     * 
     * <p>用于关联开始和结束调用。</p>
     */
    public static final class TraceToken {
        
        /**
         * 空操作令牌（追踪禁用时使用）
         */
        public static final TraceToken NOOP = new TraceToken(null);
        
        private final String traceId;
        
        TraceToken(String traceId) {
            this.traceId = traceId;
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
         * 检查是否为空操作令牌
         * 
         * @return 是空操作返回 true
         */
        public boolean isNoop() {
            return traceId == null;
        }
    }
    
    /**
     * 调用上下文（内部使用）
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
     * 可抛出异常的 Supplier
     * 
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
    
    /**
     * 可抛出异常的 Runnable
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
