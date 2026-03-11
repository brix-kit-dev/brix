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
 * Outbox Pattern Event Publishing Adapter.
 *
 * <p>This package provides a transactional event publishing mechanism based on the Outbox pattern,
 * ensuring data consistency between business operations and event publishing (AtLeastOnce semantics).</p>
 *
 * <h2>Architecture Position (v3.0 Runtime Shell Architecture Blueprint)</h2>
 * <p>
 * This module belongs to Layer 2.5 (Capability Implementation Layer / Adapter Layer), separated from
 * {@code infra-adapter-kafka}. The reason is that Outbox is a cross-infrastructure pattern (requires
 * database + message queue coordination), placing it in the Kafka adapter would introduce unnecessary
 * JPA dependencies.
 * </p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.outbox.OutboxEvent} - Outbox event JPA entity</li>
 *   <li>{@link io.infra.adapter.outbox.OutboxEventPublisher} - Scheduled publisher</li>
 *   <li>{@link io.infra.adapter.outbox.OutboxEventRepository} - Spring Data JPA repository</li>
 *   <li>{@link io.infra.adapter.outbox.config.OutboxAutoConfiguration} - Auto-configuration entry point</li>
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <p>
 * This module depends on {@code infra-adapter-kafka} to reuse
 * {@link io.infra.adapter.kafka.EventTopicResolver} and
 * {@link io.infra.adapter.kafka.config.KafkaEventBusProperties.OutboxProperties}.
 * </p>
 *
 * @since 3.0.0
 */
package io.infra.adapter.outbox;
