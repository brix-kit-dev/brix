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

/**
 * Runtime Context Management Package.
 * 
 * <p>Provides runtime context implementation and multi-tenant context management capabilities, including:</p>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.context.RegistryDrivenRuntimeContext}
 *       - Registry-driven RuntimeContext unified implementation, shared by Standalone/Embedded</li>
 *   <li>{@link io.runtime.orchestrator.context.TenantContext}
 *       - ThreadLocal-based tenant context holder</li>
 *   <li>{@link io.runtime.orchestrator.context.TenantIsolatedStateStore}
 *       - Tenant-isolated state store decorator</li>
 * </ul>
 * 
 * <h2>Architecture Notes</h2>
 * <p>These classes belong to the orchestration layer (Orchestrator), RuntimeContext implementation migrated here from runtime-sdk-api.
 * The reason is that RuntimeContext implementation and tenant context management are runtime orchestration responsibilities,
 * while runtime-sdk-api (contract layer) contains only pure interface definitions (zero dependencies).</p>
 * 
 * @since 3.0.0
 */
package io.runtime.orchestrator.context;
