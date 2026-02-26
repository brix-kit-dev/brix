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
 * 基于 OpenTelemetry 的可观测性适配器（开源）
 * 
 * <p>本包提供 ObservabilityCapability 的 OpenTelemetry 实现，
 * 支持分布式追踪、指标收集和日志关联功能。</p>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.otel.OTelObservabilityCapability} - OpenTelemetry 可观测性实现</li>
 *   <li>{@link io.infra.adapter.otel.OTelTracingCapability} - 分布式追踪实现</li>
 *   <li>{@link io.infra.adapter.otel.OTelMetricsCapability} - 指标收集实现</li>
 * </ul>
 * 
 * <h2>支持的导出器</h2>
 * <ul>
 *   <li>OTLP Exporter（推荐，支持 gRPC 和 HTTP）</li>
 *   <li>Jaeger Exporter（追踪）</li>
 *   <li>Prometheus Exporter（指标）</li>
 *   <li>Logging Exporter（开发调试）</li>
 * </ul>
 * 
 * <h2>可观测性三支柱</h2>
 * <ul>
 *   <li><b>Tracing</b>：分布式请求追踪，跨服务调用链</li>
 *   <li><b>Metrics</b>：指标收集，Counter/Gauge/Histogram</li>
 *   <li><b>Logging</b>：结构化日志，与追踪关联</li>
 * </ul>
 * 
 * <h2>架构分层</h2>
 * <p>本包属于 Layer 2 - Adapter 层，实现 Layer 1 定义的能力接口。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.otel;
