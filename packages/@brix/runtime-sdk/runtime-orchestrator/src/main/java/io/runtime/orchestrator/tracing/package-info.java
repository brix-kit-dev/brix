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
 * Capability Invocation Tracing Package.
 * 
 * <p>Provides capability invocation tracing, timing, and metrics collection.</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link io.runtime.orchestrator.tracing.CapabilityInvocationTracer} - Capability invocation tracer</li>
 *   <li>{@link io.runtime.orchestrator.tracing.CapabilityTracingConfig} - Tracing configuration</li>
 * </ul>
 * 
 * <h2>API Contracts (from runtime-sdk-api)</h2>
 * <ul>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityInvocation} - Capability invocation record</li>
 *   <li>{@link io.runtime.sdk.tracing.CapabilityMetricsExporter} - Metrics exporter interface</li>
 * </ul>
 * 
 * <h2>Architecture Notes</h2>
 * <p>This package implements Runtime Observability:
 * each {@code Capability.invoke()} records caller plugin, target capability, and duration.</p>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create tracer
 * CapabilityInvocationTracer tracer = new CapabilityInvocationTracer(config);
 * 
 * // Start tracing
 * TraceToken token = tracer.startInvocation("booking", HttpCapability.class, "sendRequest");
 * try {
 *     // Execute capability invocation
 *     capability.sendRequest(...);
 *     tracer.endSuccess(token);
 * } catch (Exception e) {
 *     tracer.endFailure(token, e);
 *     throw e;
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
package io.runtime.orchestrator.tracing;
