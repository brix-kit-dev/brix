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
 * 能力注册表实现包
 *
 * <p>本包提供 {@link io.runtime.sdk.capability.registry.CapabilityRegistry} 接口的默认实现，
 * 属于编排层（runtime-orchestrator），由各 Host 模式复用。</p>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.capability.DefaultCapabilityRegistry}
 *       - 默认能力注册表实现，支持类型安全注册、别名、冻结机制</li>
 * </ul>
 *
 * <h2>架构定位</h2>
 * <p>
 * 契约接口（{@code CapabilityRegistry}、{@code @Capability}、{@code CapabilityDescriptor}）
 * 定义在 {@code runtime-sdk-api}（契约层），本包提供其通用默认实现。
 * Standalone 和 Embedded Host 均通过此实现管理能力注册。
 * </p>
 *
 * @since 3.0.0
 */
package io.runtime.orchestrator.capability;
