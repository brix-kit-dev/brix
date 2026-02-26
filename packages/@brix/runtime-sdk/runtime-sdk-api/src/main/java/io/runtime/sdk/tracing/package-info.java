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
 * 能力调用追踪 API
 * 
 * <p>本包提供能力调用追踪的 API 契约，供运行时核心和基础设施适配器使用。</p>
 * 
 * <h2>核心接口</h2>
 * <ul>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityInvocation} - 能力调用记录数据类</li>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityMetricsExporter} - 指标导出器 SPI</li>
 * </ul>
 * 
 * <h2>架构说明</h2>
 * <p>实现 v3.0 架构蓝图 4.4 任务（运行时可观测性）的 API 层。</p>
 * 
 * @since 3.0.0
 */
package io.runtime.sdk.tracing;
