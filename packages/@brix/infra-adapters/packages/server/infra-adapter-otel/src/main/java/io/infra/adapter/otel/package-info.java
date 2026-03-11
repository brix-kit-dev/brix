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
 * OpenTelemetry-based observability adapter (open source).
 * 
 * <p>This package provides OpenTelemetry implementation of ObservabilityCapability,
 * supporting distributed tracing, metrics collection, and log correlation features.</p>
 * 
 * <h2>Core Classes</h2>
 * <ul>
 *   <li>{@link io.infra.adapter.otel.OTelObservabilityCapability} - OpenTelemetry observability implementation</li>
 *   <li>{@link io.infra.adapter.otel.OTelTracingCapability} - Distributed tracing implementation</li>
 *   <li>{@link io.infra.adapter.otel.OTelMetricsCapability} - Metrics collection implementation</li>
 * </ul>
 * 
 * <h2>Supported Exporters</h2>
 * <ul>
 *   <li>OTLP Exporter (recommended, supports gRPC and HTTP)</li>
 *   <li>Jaeger Exporter (tracing)</li>
 *   <li>Prometheus Exporter (metrics)</li>
 *   <li>Logging Exporter (development debugging)</li>
 * </ul>
 * 
 * <h2>Three Pillars of Observability</h2>
 * <ul>
 *   <li><b>Tracing</b>: Distributed request tracing, cross-service call chains</li>
 *   <li><b>Metrics</b>: Metric collection, Counter/Gauge/Histogram</li>
 *   <li><b>Logging</b>: Structured logging, correlated with traces</li>
 * </ul>
 * 
 * <h2>Architecture Layer</h2>
 * <p>This package belongs to Layer 2 - Adapter layer, implementing capability interfaces defined in Layer 1.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
package io.infra.adapter.otel;
