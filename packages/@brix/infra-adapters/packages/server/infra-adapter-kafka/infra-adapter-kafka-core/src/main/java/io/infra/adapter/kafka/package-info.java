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
 * Kafka Event Bus Capability Implementation.
 * 
 * <p>This package provides the Apache Kafka-based {@link io.runtime.sdk.capability.EventBusCapability} implementation,
 * which is one of the core components in the Runtime Shell commercial implementation layer (Layer 3: Host Layer).</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.brix.runtime.kafka.KafkaEventBusCapability} - EventBus capability implementation</li>
 *   <li>{@link io.brix.runtime.kafka.EventTopicResolver} - Topic resolution</li>
 *   <li>{@link io.brix.runtime.kafka.OutboxEventPublisher} - Outbox pattern publishing</li>
 *   <li>{@link io.brix.runtime.kafka.EventConsumerRegistry} - Consumer registry</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Follows Runtime Shell architecture constraints, does not expose Kafka details to modules</li>
 *   <li>Supports Outbox pattern to ensure transactional consistency</li>
 *   <li>Declarative event routing based on Manifest</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
package io.infra.adapter.kafka;
