/*
 * Copyright 2026 Brix Platform Authors
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
package io.runtime.orchestrator;

/**
 * Package marker interface for {@code io.runtime.orchestrator} component scanning.
 *
 * <p>Used as a type-safe anchor in {@code @ComponentScan(basePackageClasses = ...)}
 * to replace error-prone string-based {@code basePackages} declarations.
 * Covers all orchestrator sub-packages: autoconfigure, capability, context,
 * endpoint, event, lifecycle, registry, tracing, etc.</p>
 *
 * <h3>Architecture Position</h3>
 * <ul>
 *   <li><b>Layer</b>: 2C — Capability Implementation (Runtime Orchestrator)</li>
 *   <li><b>Module</b>: runtime-orchestrator</li>
 *   <li><b>Blueprint ref</b>: v3.0.9 Section 5.7 / P2-7 — @ComponentScan refinement</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 * @see org.springframework.context.annotation.ComponentScan#basePackageClasses()
 */
public interface OrchestratorMarker {
    // Marker interface — no methods
}
