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
package io.brix.platform;

/**
 * Package marker interface for {@code io.brix.platform} component scanning.
 *
 * <p>Used as a type-safe anchor in {@code @ComponentScan(basePackageClasses = ...)}
 * to replace error-prone string-based {@code basePackages} declarations.
 * Placing the marker at the root {@code io.brix.platform} package ensures that
 * all platform-commons sub-packages (auth, tenant, gateway, common, etc.) are
 * included in component scanning.</p>
 *
 * <h3>Architecture Position</h3>
 * <ul>
 *   <li><b>Layer</b>: 2C — Capability Implementation</li>
 *   <li><b>Module</b>: platform-common (foundational, depended on by all platform modules)</li>
 *   <li><b>Blueprint ref</b>: v3.0.9 Section 5.7 / P2-7 — @ComponentScan refinement</li>
 * </ul>
 *
 * <h3>Why a marker interface instead of package-info.java?</h3>
 * <p>{@code basePackageClasses} requires a {@code Class<?>} reference.
 * {@code package-info.java} does not produce a loadable class on all compilers,
 * so a dedicated marker interface is the idiomatic Spring approach.</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 * @see org.springframework.context.annotation.ComponentScan#basePackageClasses()
 */
public interface PlatformMarker {
    // Marker interface — no methods
}
