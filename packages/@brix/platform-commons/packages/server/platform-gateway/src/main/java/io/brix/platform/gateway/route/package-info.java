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
 * 路由管理包
 * 
 * <p>本包提供 API 网关的动态路由管理功能，包括：</p>
 * <ul>
 *   <li>{@link io.brix.platform.gateway.route.EventDrivenRouteRefresher} - 事件驱动路由刷新器</li>
 *   <li>{@link io.brix.platform.gateway.route.DynamicRouteService} - 动态路由服务</li>
 *   <li>{@link io.brix.platform.gateway.route.RouteDefinition} - 路由定义</li>
 * </ul>
 * 
 * <h3>v3.0 架构改造</h3>
 * <p>从 Redis Pub/Sub 迁移到 EventBus 事件订阅：</p>
 * <ul>
 *   <li>监听 ModuleStartedEvent 注册新路由</li>
 *   <li>监听 ModuleStoppedEvent 移除路由</li>
 *   <li>通过 EventBusCapability 实现，不直接依赖 Kafka/Redis</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.brix.platform.gateway.route;
