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
 * Capability auto-configuration package.
 *
 * <h2>Architecture Position</h2>
 * <p>
 * This package provides Spring Boot auto-configuration support for automatic
 * capability scanning, registration, and assembly. Common logic extracted from
 * Host layer, following the <b>Ultra-Thin Host</b> principle.
 * </p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.autoconfigure.CapabilityAutoConfiguration} —
 *       Capability auto-configuration entry point</li>
 *   <li>{@link io.runtime.orchestrator.autoconfigure.CapabilityProperties} —
 *       Capability configuration properties</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>
 * Host layer's AutoConfiguration only needs to Import configuration classes from this package:
 * </p>
 * <pre>{@code
 * @AutoConfiguration
 * @Import(CapabilityAutoConfiguration.class)
 * @EnableConfigurationProperties(StandaloneShellProperties.class)
 * public class StandaloneShellAutoConfiguration {
 *     // EMPTY — ultra-thin Host
 * }
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.4
 */
package io.runtime.orchestrator.autoconfigure;
