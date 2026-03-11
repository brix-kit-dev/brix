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
 * Gateway Event Definition Package
 * 
 * <p>Defines event types used by the gateway module, corresponding to events declared in module-manifest.yaml:</p>
 * <ul>
 *   <li>{@link io.brix.platform.gateway.event.ModuleStartedEvent} - Module started event</li>
 *   <li>{@link io.brix.platform.gateway.event.ModuleStoppedEvent} - Module stopped event</li>
 *   <li>{@link io.brix.platform.gateway.event.RouteRefreshRequestedEvent} - Route refresh request event</li>
 * </ul>
 * 
 * <h3>Event Flow</h3>
 * <p>EventBusCapability implementations convert external events (e.g., Kafka messages) to these event classes
 * and publish them via Spring ApplicationEventPublisher, allowing EventDrivenRouteRefresher
 * to receive them via @EventListener.</p>
 * 
 * <h3>Mapping to Refactoring Checklist</h3>
 * <p>Corresponds to Section 3.4 Gateway Refactoring Plan in v3.0-Code-Refactoring-Checklist.md</p>
 * 
 * @since 3.0.0
 */
package io.brix.platform.gateway.event;
