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
 * HikariCP connection pool metrics for Prometheus export.
 *
 * <p>This package provides Micrometer-based metrics collection for HikariCP
 * connection pools. Metrics are registered with the global {@link io.micrometer.core.instrument.MeterRegistry}
 * and automatically exported via the {@code /actuator/prometheus} endpoint.</p>
 *
 * <h3>Exported Metrics</h3>
 * <table>
 *   <tr><th>Metric</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>brix.hikari.connections.active</td><td>Gauge</td><td>Active connections</td></tr>
 *   <tr><td>brix.hikari.connections.idle</td><td>Gauge</td><td>Idle connections</td></tr>
 *   <tr><td>brix.hikari.connections.total</td><td>Gauge</td><td>Total connections</td></tr>
 *   <tr><td>brix.hikari.connections.pending</td><td>Gauge</td><td>Threads waiting</td></tr>
 *   <tr><td>brix.hikari.connections.max</td><td>Gauge</td><td>Max pool size</td></tr>
 *   <tr><td>brix.hikari.connections.min</td><td>Gauge</td><td>Min idle size</td></tr>
 *   <tr><td>brix.hikari.connections.usage.ratio</td><td>Gauge</td><td>Pool usage ratio</td></tr>
 * </table>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2.5 — Infrastructure Adapter (Open Source)</p>
 *
 * @since 3.1.0
 */
package io.infra.adapter.database.metrics;
