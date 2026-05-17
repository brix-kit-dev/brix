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
package io.infra.adapter;

/**
 * Package marker interface for {@code io.infra.adapter} component scanning.
 *
 * <p>Used as a type-safe anchor in {@code @ComponentScan(basePackageClasses = ...)}
 * to replace error-prone string-based {@code basePackages} declarations.
 * Placing the marker at the root {@code io.infra.adapter} package ensures that
 * all infrastructure adapter sub-packages (kafka, redis, minio, otel, simple,
 * webhook, fallback, etc.) are included in component scanning.</p>
 *
 * <h3>Architecture Position</h3>
 * <ul>
 *   <li><b>Layer</b>: 2C — Capability Implementation (Infrastructure Adapters)</li>
 *   <li><b>Module</b>: infra-adapter-fallback (always included in Host assembly)</li>
 *   <li><b>Blueprint ref</b>: v3.0.9 Section 5.7 / P2-7 — @ComponentScan refinement</li>
 * </ul>
 *
 * <p>Placed in infra-adapter-fallback because this module is a mandatory transitive
 * dependency of every Host assembly (it provides no-op fallback capabilities),
 * guaranteeing the marker class is always on the classpath.</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 * @see org.springframework.context.annotation.ComponentScan#basePackageClasses()
 */
public interface InfraAdapterMarker {
    // Marker interface — no methods
}
