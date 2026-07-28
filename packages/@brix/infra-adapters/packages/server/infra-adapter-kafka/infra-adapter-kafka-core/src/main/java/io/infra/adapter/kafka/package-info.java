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
 * Kafka Event Bus transport implementation.
 * 
 * <p>This package provides Apache Kafka based L2C adapter implementations.
 * Kafka details stay inside this package and are not exposed to plugins.</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.kafka.KafkaEventBusCapability} - EventBus capability implementation</li>
 *   <li>{@link io.infra.adapter.kafka.EventTopicResolver} - topic resolution</li>
 *   <li>{@link io.infra.adapter.kafka.CanonicalKafkaOutboxTransport} - canonical envelope transport</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Follows Runtime Shell architecture constraints and does not expose Kafka details to modules</li>
 *   <li>Maps canonical Outbox envelopes to Kafka records without owning reliability policy</li>
 *   <li>Uses Manifest-derived routing supplied by Runtime Shell</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
package io.infra.adapter.kafka;
