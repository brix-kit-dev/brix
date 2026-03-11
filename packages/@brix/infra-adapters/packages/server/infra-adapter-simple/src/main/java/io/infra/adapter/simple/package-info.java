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
 * In-Memory Infrastructure Adapter Implementation (Open Source)
 * 
 * <p>This package provides in-memory implementations of runtime-sdk-api capability interfaces,
 * designed for local development and testing scenarios. This is a lightweight implementation
 * that does not require external infrastructure like Kafka or Redis.</p>
 * 
 * <h2>Core Classes</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.simple.InMemoryEventBusCapability} - In-memory event bus</li>
 *   <li>{@link io.infra.adapter.simple.InMemoryStateStoreCapability} - In-memory state store (based on Caffeine)</li>
 *   <li>{@link io.infra.adapter.simple.InMemoryLockCapability} - In-memory distributed lock</li>
 *   <li>{@link io.infra.adapter.simple.InMemorySchedulingCapability} - In-memory scheduled tasks</li>
 * </ul>
 * 
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Local development environment (no Kafka/Redis required)</li>
 *   <li>Unit testing and integration testing</li>
 *   <li>Rapid prototyping</li>
 *   <li>Demo environments</li>
 * </ul>
 * 
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Data is stored only in memory, lost after process restart</li>
 *   <li>Cross-process communication is not supported</li>
 *   <li>Cluster deployment is not supported</li>
 *   <li>High availability is not guaranteed</li>
 * </ul>
 * 
 * <h2>Architecture Layer</h2>
 * <p>This package belongs to Layer 2 - Adapter Layer, implementing capability interfaces defined in Layer 1.</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.simple;
