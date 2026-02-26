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
 * 基于 OpenTelemetry 的能力调用指标导出器
 * 
 * <p>将能力调用指标导出到 OpenTelemetry，支持 Prometheus 格式导出。</p>
 * 
 * <h2>导出的指标</h2>
 * <table border="1">
 *   <tr><th>指标名</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>brix_capability_call_total</td><td>Counter</td><td>能力调用总数</td></tr>
 *   <tr><td>brix_capability_call_latency_seconds</td><td>Histogram</td><td>能力调用延迟分布</td></tr>
 *   <tr><td>brix_capability_active_calls</td><td>UpDownCounter</td><td>当前活跃调用数</td></tr>
 *   <tr><td>brix_eventbus_direct_bypass_total</td><td>Counter</td><td>事件总线绕过次数</td></tr>
 *   <tr><td>brix_architecture_violations_runtime</td><td>Counter</td><td>运行时架构违规</td></tr>
 * </table>
 * 
 * <h2>标签（Labels）</h2>
 * <ul>
 *   <li>{@code plugin} - 插件 ID</li>
 *   <li>{@code capability} - 能力名称</li>
 *   <li>{@code method} - 方法名</li>
 *   <li>{@code status} - 调用状态（success/error）</li>
 *   <li>{@code error_type} - 错误类型（失败时）</li>
 * </ul>
 * 
 * <h2>架构说明</h2>
 * <p>本类实现 v3.0 架构蓝图 4.4-4 任务：
 * 在 infra-adapter-otel 中增加架构合规 Metrics 导出（Prometheus 格式）。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see CapabilityMetricsExporter
 */
public class OTelCapabilityMetricsExporter implements CapabilityMetricsExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(OTelCapabilityMetricsExporter.class);
    
    /**
     * 服务名称（用于指标标签）
     */
    private static final String SERVICE_NAME = "brix-runtime";
    
    // ==================== 标签键 ====================
    
    private static final AttributeKey<String> PLUGIN_KEY = AttributeKey.stringKey("plugin");
    private static final AttributeKey<String> CAPABILITY_KEY = AttributeKey.stringKey("capability");
    private static final AttributeKey<String> METHOD_KEY = AttributeKey.stringKey("method");
    private static final AttributeKey<String> STATUS_KEY = AttributeKey.stringKey("status");
    private static final AttributeKey<String> ERROR_TYPE_KEY = AttributeKey.stringKey("error_type");
    private static final AttributeKey<String> VIOLATION_TYPE_KEY = AttributeKey.stringKey("violation_type");
    private static final AttributeKey<String> EVENT_TYPE_KEY = AttributeKey.stringKey("event_type");
    private static final AttributeKey<String> SOURCE_PLUGIN_KEY = AttributeKey.stringKey("source_plugin");
    private static final AttributeKey<String> DETAILS_KEY = AttributeKey.stringKey("details");
    
    // ==================== 指标实例 ====================
    
    /**
     * 能力调用总数计数器
     */
    private final LongCounter callTotalCounter;
    
    /**
     * 能力调用延迟直方图
     */
    private final DoubleHistogram callLatencyHistogram;
    
    /**
     * 当前活跃调用数
     */
    private final LongUpDownCounter activeCallsCounter;
    
    /**
     * 事件总线绕过计数器
     */
    private final LongCounter eventBusBypassCounter;
    
    /**
     * 架构违规计数器
     */
    private final LongCounter architectureViolationsCounter;
    
    /**
     * 创建 OTel 能力指标导出器
     * 
     * @param openTelemetry OpenTelemetry 实例
     */
    public OTelCapabilityMetricsExporter(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry cannot be null");
        
        Meter meter = openTelemetry.getMeter(SERVICE_NAME);
        
        // 初始化能力调用总数计数器
        this.callTotalCounter = meter.counterBuilder(CAPABILITY_CALL_TOTAL)
            .setDescription("能力调用总数")
            .setUnit("calls")
            .build();
        
        // 初始化能力调用延迟直方图
        this.callLatencyHistogram = meter.histogramBuilder(CAPABILITY_CALL_LATENCY)
            .setDescription("能力调用延迟分布（秒）")
            .setUnit("seconds")
            .setExplicitBucketBoundariesAdvice(java.util.Arrays.asList(
                0.001, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
            ))
            .build();
        
        // 初始化当前活跃调用数
        this.activeCallsCounter = meter.upDownCounterBuilder(CAPABILITY_ACTIVE_CALLS)
            .setDescription("当前活跃能力调用数")
            .setUnit("calls")
            .build();
        
        // 初始化事件总线绕过计数器
        this.eventBusBypassCounter = meter.counterBuilder(EVENTBUS_BYPASS_TOTAL)
            .setDescription("绕过事件总线的直接调用次数（应为 0）")
            .setUnit("calls")
            .build();
        
        // 初始化架构违规计数器
        this.architectureViolationsCounter = meter.counterBuilder(ARCHITECTURE_VIOLATIONS)
            .setDescription("运行时架构违规次数")
            .setUnit("violations")
            .build();
        
        logger.info("OTel 能力指标导出器已初始化");
    }
    
    @Override
    public void recordInvocation(CapabilityInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation cannot be null");
        
        String status = invocation.isSuccess() ? "success" : "error";
        
        // 构建标签
        AttributesBuilder attrsBuilder = Attributes.builder()
            .put(PLUGIN_KEY, invocation.getSourcePlugin())
            .put(CAPABILITY_KEY, invocation.getTargetCapability())
            .put(METHOD_KEY, invocation.getMethodName())
            .put(STATUS_KEY, status);
        
        if (!invocation.isSuccess() && invocation.getErrorType() != null) {
            attrsBuilder.put(ERROR_TYPE_KEY, invocation.getErrorType());
        }
        
        Attributes attrs = attrsBuilder.build();
        
        // 记录调用计数
        callTotalCounter.add(1, attrs);
        
        // 记录调用延迟（转换为秒）
        double latencySeconds = invocation.getDurationMs() / 1000.0;
        callLatencyHistogram.record(latencySeconds, attrs);
        
        if (logger.isTraceEnabled()) {
            logger.trace("记录能力调用指标: plugin={}, capability={}, method={}, status={}, latency={}ms",
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
        
        logger.warn("检测到事件总线绕过: eventType={}, sourcePlugin={}",
            eventType, sourcePlugin);
    }
    
    @Override
    public void recordArchitectureViolation(String violationType, String details) {
        Attributes attrs = Attributes.builder()
            .put(VIOLATION_TYPE_KEY, violationType)
            .put(DETAILS_KEY, details)
            .build();
        
        architectureViolationsCounter.add(1, attrs);
        
        logger.warn("检测到运行时架构违规: type={}, details={}",
            violationType, details);
    }
    
    @Override
    public void close() {
        logger.info("OTel 能力指标导出器已关闭");
        // OpenTelemetry SDK 会自动处理资源清理
    }
}
