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
 * 网关事件定义包
 * 
 * <p>定义网关模块使用的事件类型，与 module-manifest.yaml 中声明的事件对应：</p>
 * <ul>
 *   <li>{@link io.brix.platform.gateway.event.ModuleStartedEvent} - 模块启动事件</li>
 *   <li>{@link io.brix.platform.gateway.event.ModuleStoppedEvent} - 模块停止事件</li>
 *   <li>{@link io.brix.platform.gateway.event.RouteRefreshRequestedEvent} - 路由刷新请求事件</li>
 * </ul>
 * 
 * <h3>事件流转</h3>
 * <p>EventBusCapability 实现会将外部事件（如 Kafka 消息）转换为这些事件类，
 * 并通过 Spring ApplicationEventPublisher 发布，使得 EventDrivenRouteRefresher
 * 可以通过 @EventListener 接收。</p>
 * 
 * <h3>与改造清单的对应</h3>
 * <p>对应《v3.0-代码改造清单.md》中 3.4 节网关改造方案</p>
 * 
 * @since 3.0.0
 */
package io.brix.platform.gateway.event;
