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
 * 能力自动配置包
 *
 * <h2>架构定位（v3.0.4 架构红线修复）</h2>
 * <p>
 * 本包提供 Spring Boot 自动配置支持，实现能力的自动扫描、注册和组装。
 * 从 Host 层提取的公共逻辑，遵循 <b>Host 极薄化</b> 原则。
 * </p>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.autoconfigure.CapabilityAutoConfiguration} —
 *       能力自动配置入口</li>
 *   <li>{@link io.runtime.orchestrator.autoconfigure.CapabilityProperties} —
 *       能力配置属性</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <p>
 * Host 层的 AutoConfiguration 只需 Import 本包的配置类：
 * </p>
 * <pre>{@code
 * @AutoConfiguration
 * @Import(CapabilityAutoConfiguration.class)
 * @EnableConfigurationProperties(StandaloneShellProperties.class)
 * public class StandaloneShellAutoConfiguration {
 *     // EMPTY — ultra-thin Host
 * }
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.4
 */
package io.runtime.orchestrator.autoconfigure;
