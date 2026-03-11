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
 * Capability Contract Package
 * 
 * <p>This package defines all capability contract interfaces for the Runtime Shell,
 * serving as the standard protocol for module-Runtime Shell interaction.</p>
 * 
 * <h3>Core Capabilities (Required)</h3>
 * <ul>
 *   <li>{@link EventBusCapability} - EventBus Capability</li>
 *   <li>{@link StateStoreCapability} - State Store Capability</li>
 *   <li>{@link AuthContextCapability} - Auth Context Capability</li>
 *   <li>{@link ObservabilityCapability} - Observability Capability</li>
 *   <li>{@link ConfigStoreCapability} - Config Store Capability</li>
 *   <li>{@link LifecycleCapability} - Lifecycle Capability</li>
 * </ul>
 * 
 * <h3>Optional Capabilities</h3>
 * <ul>
 *   <li>{@link SchedulingCapability} - Scheduling Capability</li>
 *   <li>{@link LockCapability} - Distributed Lock Capability</li>
 *   <li>{@link ResilienceCapability} - Resilience Capability (Circuit Breaker/Rate Limiting)</li>
 * </ul>
 * 
 * <h3>Design Principles</h3>
 * <ol>
 *   <li>Modules only depend on interfaces in this package, not concrete implementations</li>
 *   <li>Interface design remains stable; implementations can be substituted</li>
 *   <li>Optional capabilities return Optional to avoid null pointers</li>
 * </ol>
 * 
 * @since 3.0.0
 */
