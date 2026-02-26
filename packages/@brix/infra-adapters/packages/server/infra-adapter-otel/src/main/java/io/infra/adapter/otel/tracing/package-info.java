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

/**
 * OpenTelemetry 追踪与指标导出包
 * 
 * <p>提供基于 OpenTelemetry 的能力调用追踪和指标导出功能。</p>
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.otel.tracing.OTelCapabilityMetricsExporter} - 能力调用指标导出器</li>
 * </ul>
 * 
 * <h2>架构说明</h2>
 * <p>本包实现 v3.0 架构蓝图 4.4-4 任务：
 * 在 infra-adapter-otel 中增加架构合规 Metrics 导出（Prometheus 格式）。</p>
 * 
 * <h2>导出的指标</h2>
 * <pre>
 * brix_capability_call_total{plugin="booking", capability="HttpCapability"}  → 能力调用计数
 * brix_capability_call_latency_seconds{plugin="booking"}                     → 调用延迟分布
 * brix_capability_active_calls{plugin="booking"}                              → 活跃调用数
 * brix_eventbus_direct_bypass_total                                          → 事件总线绕过次数
 * brix_architecture_violations_runtime{type="..."}                           → 运行时架构违规
 * </pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.otel.tracing;
