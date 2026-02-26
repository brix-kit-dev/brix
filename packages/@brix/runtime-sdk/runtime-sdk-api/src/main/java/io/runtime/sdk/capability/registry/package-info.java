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
 * 能力注册表模块
 * 
 * <p>提供声明式的能力注册与发现机制，是 Runtime Shell 架构的核心组件。</p>
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.runtime.sdk.capability.registry.CapabilityRegistry} - 能力注册表接口</li>
 *   <li>{@link io.runtime.sdk.capability.registry.Capability} - 能力标注注解</li>
 *   <li>{@link io.runtime.sdk.capability.registry.CapabilityDescriptor} - 能力描述符</li>
 *   <li>{@link io.runtime.sdk.capability.registry.CapabilityLevel} - 能力级别枚举</li>
 * </ul>
 * 
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>声明式组装</b> - 能力通过配置声明，而非代码硬编码</li>
 *   <li><b>类型安全</b> - 通过泛型确保类型安全的能力获取</li>
 *   <li><b>可扩展</b> - 新能力无需修改核心代码，只需注册</li>
 *   <li><b>可观测</b> - 提供能力元数据查询能力</li>
 * </ul>
 * 
 * <h2>业界参考</h2>
 * <ul>
 *   <li>OSGi Service Registry</li>
 *   <li>Kubernetes API Extensions</li>
 *   <li>VS Code Extension Capabilities</li>
 *   <li>Eclipse RCP Service Registry</li>
 * </ul>
 * 
 * @since 3.0.0
 */
package io.runtime.sdk.capability.registry;
