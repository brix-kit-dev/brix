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
 * 数据库能力适配器实现包
 * 
 * <p>本包提供基于 HikariCP 的 {@link io.runtime.sdk.capability.DatabaseCapability} 实现，
 * 是基础设施适配器层（Layer 2.5: Adapter 层）的组件之一。</p>
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.database.HikariDatabaseCapability} - 基于 HikariCP 的数据库能力实现</li>
 * </ul>
 * 
 * <h2>设计原则</h2>
 * <ul>
 *   <li>遵循运行壳架构约束，不暴露数据库驱动细节给插件</li>
 *   <li>支持配置驱动的多数据库厂商切换</li>
 *   <li>由 Host 层通过依赖注入组装</li>
 * </ul>
 * 
 * <h2>蓝图对照</h2>
 * <p>对应蓝图 v3.0.2 第 3.3.1 节「DatabaseCapability - 数据库能力」。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 */
package io.infra.adapter.database;
