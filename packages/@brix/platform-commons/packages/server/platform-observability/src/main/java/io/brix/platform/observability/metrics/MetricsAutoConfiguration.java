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
package io.brix.platform.observability.metrics;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

/**
 * Metrics auto-configuration.
 * 
 * <p>v2.1 Phase 4 Observability Enhancement</p>
 * 
 * <p>Auto-assembled components:</p>
 * <ul>
 *   <li>{@link BusinessMetrics} - Business metrics collector</li>
 *   <li>{@link JvmMetricsCollector} - JVM metrics collector</li>
 *   <li>{@link OutboxMetricsCollector} - Outbox metrics collector</li>
 *   <li>{@link CircuitBreakerMetricsCollector} - Circuit breaker metrics collector</li>
 * </ul>
 * 
 * <p>Configuration:</p>
 * <pre>
 * observability:
 *   metrics:
 *     enabled: true          # Master switch
 *     jvm:
 *       enabled: true        # JVM metrics
 *     outbox:
 *       enabled: true        # Outbox metrics
 *     circuit-breaker:
 *       enabled: true        # Circuit breaker metrics
 * </pre>
 * 
 * <p>Prometheus Endpoint</p>
 * <pre>
 * GET /actuator/prometheus
 * </pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 2.1.0 (Phase 4 Enhancement)
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = MetricsAutoConfiguration.class)
public class MetricsAutoConfiguration {

    // Components in this package are auto-scanned via @ComponentScan
}

