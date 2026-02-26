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
 * 基于内存的基础设施适配器实现（开源）
 * 
 * <p>本包提供 runtime-sdk-api 能力接口的内存实现，用于本地开发和测试场景。
 * 这是一个轻量级的实现，无需依赖 Kafka、Redis 等外部基础设施。</p>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.simple.InMemoryEventBusCapability} - 内存事件总线</li>
 *   <li>{@link io.infra.adapter.simple.InMemoryStateStoreCapability} - 内存状态存储（基于 Caffeine）</li>
 *   <li>{@link io.infra.adapter.simple.InMemoryLockCapability} - 内存分布式锁</li>
 *   <li>{@link io.infra.adapter.simple.InMemorySchedulingCapability} - 内存定时任务</li>
 * </ul>
 * 
 * <h2>适用场景</h2>
 * <ul>
 *   <li>本地开发环境（无需 Kafka/Redis）</li>
 *   <li>单元测试和集成测试</li>
 *   <li>快速原型验证</li>
 *   <li>演示环境</li>
 * </ul>
 * 
 * <h2>限制说明</h2>
 * <ul>
 *   <li>数据仅存储在内存中，进程重启后丢失</li>
 *   <li>不支持跨进程通信</li>
 *   <li>不支持集群部署</li>
 *   <li>不保证高可用性</li>
 * </ul>
 * 
 * <h2>架构分层</h2>
 * <p>本包属于 Layer 2 - Adapter 层，实现 Layer 1 定义的能力接口。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.simple;
