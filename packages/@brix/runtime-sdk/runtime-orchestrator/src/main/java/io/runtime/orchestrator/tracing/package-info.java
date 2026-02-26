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

/**
 * 能力调用追踪包
 * 
 * <p>提供能力调用的追踪、计时和指标收集功能。</p>
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.tracing.CapabilityInvocationTracer} - 能力调用追踪器</li>
 *   <li>{@link io.runtime.orchestrator.tracing.CapabilityTracingConfig} - 追踪配置</li>
 * </ul>
 * 
 * <h2>API 契约（来自 runtime-sdk-api）</h2>
 * <ul>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityInvocation} - 能力调用记录</li>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityMetricsExporter} - 指标导出器接口</li>
 * </ul>
 * 
 * <h2>架构说明</h2>
 * <p>本包实现 v3.0 架构蓝图中 4.4-1 任务：
 * 每次 {@code Capability.invoke()} 记录调用方插件、目标能力、耗时。</p>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建追踪器
 * CapabilityInvocationTracer tracer = new CapabilityInvocationTracer(config);
 * 
 * // 开始追踪
 * TraceToken token = tracer.startInvocation("booking", HttpCapability.class, "sendRequest");
 * try {
 *     // 执行能力调用
 *     capability.sendRequest(...);
 *     tracer.endSuccess(token);
 * } catch (Exception e) {
 *     tracer.endFailure(token, e);
 *     throw e;
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
package io.runtime.orchestrator.tracing;
