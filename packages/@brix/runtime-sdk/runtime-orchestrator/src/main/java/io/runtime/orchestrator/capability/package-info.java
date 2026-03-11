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
 * Capability Registry Implementation Package.
 *
 * <p>This package provides default implementation of {@link io.runtime.sdk.capability.registry.CapabilityRegistry} interface,
 * belonging to the orchestration layer (runtime-orchestrator), reused by various Host modes.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.capability.DefaultCapabilityRegistry}
 *       - Default capability registry implementation, supporting type-safe registration, aliases, freeze mechanism</li>
 * </ul>
 *
 * <h2>Architecture Position</h2>
 * <p>
 * Contract interfaces ({@code CapabilityRegistry}, {@code @Capability}, {@code CapabilityDescriptor})
 * are defined in {@code runtime-sdk-api} (contract layer), this package provides their common default implementation.
 * Both Standalone and Embedded Hosts manage capability registration through this implementation.
 * </p>
 *
 * @since 3.0.0
 */
package io.runtime.orchestrator.capability;
