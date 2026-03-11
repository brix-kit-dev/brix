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
 * Capability Invocation Tracing API.
 * 
 * <p>This package provides API contracts for capability invocation tracing,
 * used by runtime core and infrastructure adapters.</p>
 * 
 * <h2>Core Interfaces</h2>
 * <ul>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityInvocation} - Capability invocation record data class</li>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityMetricsExporter} - Metrics exporter SPI</li>
 * </ul>
 * 
 * <h2>Architecture Notes</h2>
 * <p>Implements the Runtime Observability API layer for capability tracking.</p>
 * 
 * @since 3.0.0
 */
package io.runtime.sdk.tracing;
