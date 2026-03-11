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
 * Route Management Package
 * 
 * <p>This package provides API Gateway dynamic route management functionality, including:</p>
 * <ul>
 *   <li>{@link io.brix.platform.gateway.route.EventDrivenRouteRefresher} - Event-driven route refresher</li>
 *   <li>{@link io.brix.platform.gateway.route.DynamicRouteService} - Dynamic route service</li>
 *   <li>{@link io.brix.platform.gateway.route.RouteDefinition} - Route definition</li>
 * </ul>
 * 
 * <h3>v3.0 Architecture Refactoring</h3>
 * <p>Migration from Redis Pub/Sub to EventBus event subscription:</p>
 * <ul>
 *   <li>Listen to ModuleStartedEvent to register new routes</li>
 *   <li>Listen to ModuleStoppedEvent to remove routes</li>
 *   <li>Implemented via EventBusCapability, no direct dependency on Kafka/Redis</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
package io.brix.platform.gateway.route;
