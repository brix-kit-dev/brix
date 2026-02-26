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
 * Redis 状态存储能力实现包
 * 
 * <p>本包提供基于 Redis {@link io.runtime.sdk.capability.StateStoreCapability} 
 * {@link io.runtime.sdk.capability.LockCapability} 实现
 * 是运行壳商业实现层（Layer 3: Host 层）的核心组件之一。</p>
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.shinwa.runtime.redis.RedisStateStoreCapability} - StateStore 能力实现</li>
 *   <li>{@link com.shinwa.runtime.redis.RedisLockCapability} - Lock 能力实现</li>
 * </ul>
 * 
 * <h2>设计原则</h2>
 * <ul>
 *   <li>遵循运行壳架构约束，不暴Redis 细节给模。</li>
 *   <li>支持 JSON 序列化存储复杂对。</li>
 *   <li>使用 Redisson 实现分布式锁</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
package io.infra.adapter.redis;
