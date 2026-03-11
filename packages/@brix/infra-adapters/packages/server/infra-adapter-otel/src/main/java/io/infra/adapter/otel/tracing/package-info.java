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
 * OpenTelemetry tracing and metrics export package.
 * 
 * <p>Provides OpenTelemetry-based capability invocation tracing and metrics export functionality.</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.otel.tracing.OTelCapabilityMetricsExporter} - Capability invocation metrics exporter</li>
 * </ul>
 * 
 * <h2>Architecture Notes</h2>
 * <p>This package implements v3.0 architecture blueprint task 4.4-4:
 * Add architecture compliance metrics export in infra-adapter-otel (Prometheus format).</p>
 * 
 * <h2>Exported Metrics</h2>
 * <pre>
 * brix_capability_call_total{plugin="booking", capability="HttpCapability"}  → Capability invocation count
 * brix_capability_call_latency_seconds{plugin="booking"}                     → Invocation latency distribution
 * brix_capability_active_calls{plugin="booking"}                              → Active invocations
 * brix_eventbus_direct_bypass_total                                          → Event bus bypass count
 * brix_architecture_violations_runtime{type="..."}                           → Runtime architecture violations
 * </pre>
 *
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.otel.tracing;
