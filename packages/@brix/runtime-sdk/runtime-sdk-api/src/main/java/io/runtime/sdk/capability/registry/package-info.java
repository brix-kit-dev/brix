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
 * Capability Registry Module
 * 
 * <p>Provides declarative capability registration and discovery mechanism,
 * serving as the core component of Runtime Shell architecture.</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.runtime.sdk.capability.registry.CapabilityRegistry} - Capability Registry Interface</li>
 *   <li>{@link io.runtime.sdk.capability.registry.Capability} - Capability annotation</li>
 *   <li>{@link io.runtime.sdk.capability.registry.CapabilityDescriptor} - Capability descriptor</li>
 *   <li>{@link io.runtime.sdk.capability.registry.CapabilityLevel} - Capability level enumeration</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Declarative Assembly</b> - Capabilities declared via configuration, not hardcoded</li>
 *   <li><b>Type Safety</b> - Type-safe capability retrieval through generics</li>
 *   <li><b>Extensible</b> - New capabilities require no core code changes, just registration</li>
 *   <li><b>Observable</b> - Provides capability metadata query functionality</li>
 * </ul>
 * 
 * <h2>Industry Reference</h2>
 * <ul>
 *   <li>OSGi Service Registry</li>
 *   <li>Kubernetes API Extensions</li>
 *   <li>VS Code Extension Capabilities</li>
 *   <li>Eclipse RCP Service Registry</li>
 * </ul>
 * 
 * @since 3.0.0
 */
package io.runtime.sdk.capability.registry;
