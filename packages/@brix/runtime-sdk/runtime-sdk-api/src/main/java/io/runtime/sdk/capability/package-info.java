/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

/**
 * 能力契约包
 * 
 * <p>本包定义了运行壳的所有能力契约接口，是模块与 Runtime Shell 交互的标准协议。</p>
 * 
 * <h3>核心能力（必须实现）</h3>
 * <ul>
 *   <li>{@link EventBusCapability} - 事件总线能力</li>
 *   <li>{@link StateStoreCapability} - 状态存储能力</li>
 *   <li>{@link AuthContextCapability} - 认证上下文能力</li>
 *   <li>{@link ObservabilityCapability} - 可观测性能力</li>
 *   <li>{@link ConfigStoreCapability} - 配置存储能力</li>
 *   <li>{@link LifecycleCapability} - 生命周期能力</li>
 * </ul>
 * 
 * <h3>可选能力</h3>
 * <ul>
 *   <li>{@link SchedulingCapability} - 定时任务能力</li>
 *   <li>{@link LockCapability} - 分布式锁能力</li>
 *   <li>{@link ResilienceCapability} - 韧性能力（熔断/限流）</li>
 * </ul>
 * 
 * <h3>设计原则</h3>
 * <ol>
 *   <li>模块只依赖此包中的接口，不依赖具体实现</li>
 *   <li>接口设计保持稳定，实现可以替换</li>
 *   <li>可选能力通过 Optional 返回，避免空指针</li>
 * </ol>
 * 
 * @since 3.0.0
 */
