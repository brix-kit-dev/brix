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
 * Kafka 事件总线能力实现
 * 
 * <p>本包提供基于 Apache Kafka {@link io.runtime.sdk.capability.EventBusCapability} 实现
 * 是运行壳商业实现层（Layer 3: Host 层）的核心组件之一。</p>
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.shinwa.runtime.kafka.KafkaEventBusCapability} - EventBus 能力实现</li>
 *   <li>{@link com.shinwa.runtime.kafka.EventTopicResolver} - Topic 解析。</li>
 *   <li>{@link com.shinwa.runtime.kafka.OutboxEventPublisher} - Outbox 模式发布。</li>
 *   <li>{@link com.shinwa.runtime.kafka.EventConsumerRegistry} - 消费者注册中。</li>
 * </ul>
 * 
 * <h2>设计原则</h2>
 * <ul>
 *   <li>遵循运行壳架构约束，不暴Kafka 细节给模。</li>
 *   <li>支持 Outbox 模式保证事务一致。</li>
 *   <li>基于 Manifest 声明式事件路。</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
package io.infra.adapter.kafka;
