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
package io.runtime.sdk.event;

/**
 * 事件包
 * 
 * <p>本包定义了运行壳事件模型的核心类：</p>
 * <ul>
 *   <li>{@link DomainEvent} - 领域事件基类，用于模块内部事件</li>
 *   <li>{@link IntegrationEvent} - 集成事件基类，用于跨模块/跨系统通信</li>
 * </ul>
 * 
 * <h3>事件设计原则</h3>
 * <ol>
 *   <li>事件是不可变的（Immutable）</li>
 *   <li>事件表示已经发生的事实（Past Tense）</li>
 *   <li>事件必须可序列化（Serializable）</li>
 *   <li>事件消费必须支持幂等（Idempotent）</li>
 * </ol>
 * 
 * @since 3.0.0
 */
