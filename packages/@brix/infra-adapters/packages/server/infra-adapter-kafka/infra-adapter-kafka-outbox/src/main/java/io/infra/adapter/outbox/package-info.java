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
 * Outbox 模式事件发布适配器
 *
 * <p>本包提供基于 Outbox 模式的事务性事件发布机制，保证
 * 业务操作与事件发布的数据一致性（AtLeastOnce 语义）。</p>
 *
 * <h2>架构定位（v3.0 运行壳架构蓝图）</h2>
 * <p>
 * 本模块属于 Layer 2.5（能力实现层 / Adapter 层），从 {@code infra-adapter-kafka}
 * 中独立出来。原因是 Outbox 是跨基础设施的模式（需要数据库 + 消息队列协同），
 * 将其放在 Kafka 适配器中会引入不必要的 JPA 依赖。
 * </p>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.outbox.OutboxEvent} - Outbox 事件 JPA 实体</li>
 *   <li>{@link io.infra.adapter.outbox.OutboxEventPublisher} - 定时发布器</li>
 *   <li>{@link io.infra.adapter.outbox.OutboxEventRepository} - Spring Data JPA 仓储</li>
 *   <li>{@link io.infra.adapter.outbox.config.OutboxAutoConfiguration} - 自动配置入口</li>
 * </ul>
 *
 * <h2>依赖关系</h2>
 * <p>
 * 本模块依赖 {@code infra-adapter-kafka} 以复用
 * {@link io.infra.adapter.kafka.EventTopicResolver} 和
 * {@link io.infra.adapter.kafka.config.KafkaEventBusProperties.OutboxProperties}。
 * </p>
 *
 * @since 3.0.0
 */
package io.infra.adapter.outbox;
