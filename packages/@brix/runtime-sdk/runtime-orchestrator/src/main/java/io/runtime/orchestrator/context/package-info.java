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
 * 运行时上下文管理包
 * 
 * <p>提供运行时上下文实现和多租户上下文管理能力，包括：</p>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.context.RegistryDrivenRuntimeContext}
 *       — 基于注册表驱动的 RuntimeContext 统一实现，Standalone/Embedded 共享</li>
 *   <li>{@link io.runtime.orchestrator.context.TenantContext}
 *       — 基于 ThreadLocal 的租户上下文持有器</li>
 *   <li>{@link io.runtime.orchestrator.context.TenantIsolatedStateStore}
 *       — 租户隔离的状态存储装饰器</li>
 * </ul>
 * 
 * <h2>架构说明</h2>
 * <p>这些类属于编排层（Orchestrator），RuntimeContext 实现从 runtime-sdk-api 迁移至此。
 * 原因是 RuntimeContext 实现和租户上下文管理属于运行时编排职责，
 * 而 runtime-sdk-api（契约层）仅包含纯接口定义（零依赖）。</p>
 * 
 * @since 3.0.0
 */
package io.runtime.orchestrator.context;
