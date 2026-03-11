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
 * Redis state store capability implementation package.
 * 
 * <p>This package provides Redis-based implementations of {@link io.runtime.sdk.capability.StateStoreCapability} 
 * and {@link io.runtime.sdk.capability.LockCapability}.
 * It is one of the core components of the Runtime Shell Commercial Implementation Layer (Layer 3: Host Layer).</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.brix.runtime.redis.RedisStateStoreCapability} - StateStore capability implementation</li>
 *   <li>{@link io.brix.runtime.redis.RedisLockCapability} - Lock capability implementation</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Follows runtime shell architecture constraints, does not expose Redis details to modules.</li>
 *   <li>Supports JSON serialization for storing complex objects.</li>
 *   <li>Uses Redisson for distributed lock implementation</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
package io.infra.adapter.redis;
