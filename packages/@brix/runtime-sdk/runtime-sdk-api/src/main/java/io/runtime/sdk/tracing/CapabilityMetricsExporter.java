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
 * 能力调用指标导出器接口
 * 
 * <p>定义能力调用指标的导出契约，支持不同的后端实现（Prometheus、OTel 等）。</p>
 * 
 * <h2>指标类型</h2>
 * <ul>
 *   <li><b>Counter</b>：调用次数、成功/失败次数</li>
 *   <li><b>Histogram</b>：调用耗时分布</li>
 *   <li><b>Gauge</b>：当前活跃调用数</li>
 * </ul>
 * 
 * <h2>标准指标名称</h2>
 * <pre>
 * brix_capability_call_total{plugin="booking", capability="HttpCapability", method="sendRequest", status="success"}
 * brix_capability_call_latency_seconds{plugin="booking", capability="HttpCapability", method="sendRequest"}
 * brix_capability_active_calls{plugin="booking", capability="HttpCapability"}
 * brix_eventbus_direct_bypass_total
 * brix_architecture_violations_runtime{type="direct_capability_call"}
 * </pre>
 * 
 * <h2>实现要求</h2>
 * <ol>
 *   <li>导出器必须线程安全</li>
 *   <li>导出失败不应阻塞业务调用</li>
 *   <li>指标命名遵循 Prometheus 命名规范</li>
 * </ol>
 * 
 * <h2>架构说明</h2>
 * <p>本接口是 v3.0 架构蓝图 4.4 任务的 SPI 定义。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface CapabilityMetricsExporter {
    
    /**
     * 指标前缀
     */
    String METRIC_PREFIX = "brix_";
    
    /**
     * 能力调用总数指标名
     */
    String CAPABILITY_CALL_TOTAL = METRIC_PREFIX + "capability_call_total";
    
    /**
     * 能力调用延迟指标名（Histogram）
     */
    String CAPABILITY_CALL_LATENCY = METRIC_PREFIX + "capability_call_latency_seconds";
    
    /**
     * 当前活跃调用数指标名
     */
    String CAPABILITY_ACTIVE_CALLS = METRIC_PREFIX + "capability_active_calls";
    
    /**
     * 事件总线绕过计数指标名
     */
    String EVENTBUS_BYPASS_TOTAL = METRIC_PREFIX + "eventbus_direct_bypass_total";
    
    /**
     * 运行时架构违规指标名
     */
    String ARCHITECTURE_VIOLATIONS = METRIC_PREFIX + "architecture_violations_runtime";
    
    /**
     * 记录能力调用
     * 
     * <p>记录单次能力调用的指标数据，包括：</p>
     * <ul>
     *   <li>调用计数（Counter）</li>
     *   <li>调用耗时（Histogram）</li>
     *   <li>成功/失败状态</li>
     * </ul>
     * 
     * @param invocation 能力调用记录
     */
    void recordInvocation(CapabilityInvocation invocation);
    
    /**
     * 增加活跃调用计数
     * 
     * <p>当能力调用开始时调用，用于追踪当前并发调用数。</p>
     * 
     * @param pluginId 插件 ID
     * @param capabilityName 能力名称
     */
    void incrementActiveCall(String pluginId, String capabilityName);
    
    /**
     * 减少活跃调用计数
     * 
     * <p>当能力调用结束时调用（无论成功或失败）。</p>
     * 
     * @param pluginId 插件 ID
     * @param capabilityName 能力名称
     */
    void decrementActiveCall(String pluginId, String capabilityName);
    
    /**
     * 记录事件总线绕过
     * 
     * <p>当检测到直接绕过事件总线的调用时记录。</p>
     * 
     * @param eventType 事件类型
     * @param sourcePlugin 源插件 ID
     */
    void recordEventBusBypass(String eventType, String sourcePlugin);
    
    /**
     * 记录架构违规
     * 
     * <p>记录运行时检测到的架构违规。</p>
     * 
     * @param violationType 违规类型
     * @param details 违规详情
     */
    void recordArchitectureViolation(String violationType, String details);
    
    /**
     * 关闭导出器
     * 
     * <p>释放资源，刷新未发送的指标。</p>
     */
    default void close() {
        // 默认空实现
    }
}
