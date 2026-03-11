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
package io.brix.platform.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import io.brix.platform.observability.health.HealthCheckAutoConfiguration;
import io.brix.platform.observability.logging.LoggingAutoConfiguration;
import io.brix.platform.observability.metrics.MetricsAutoConfiguration;
import io.brix.platform.observability.tracing.TracingAutoConfiguration;

/**
 * Observability Auto Configuration - Standard v1.0
 * <p>
 * Unified entry point that aggregates the following observability capabilities:
 * <ul>
 *   <li>Tracing - TraceId propagation, context management</li>
 *   <li>Logging - Structured logging, MDC injection</li>
 *   <li>Health - Redis/Kafka health indicators</li>
 *   <li>Metrics - Cache hit rate and other business metrics</li>
 * </ul>
 * </p>
 * 
 * <h3>Configuration Example</h3>
 * <pre>{@code
 * observability:
 *   tracing:
 *     enabled: true
 *     propagation-headers:
 *       - X-Trace-ID
 *       - X-Request-ID
 *   logging:
 *     format: json
 *     include-request-body: false
 *   health:
 *     redis:
 *       enabled: true
 *     kafka:
 *       enabled: true
 *   metrics:
 *     cache:
 *       enabled: true
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0 (Standardization v1.0)
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
    TracingAutoConfiguration.class,
    LoggingAutoConfiguration.class,
    HealthCheckAutoConfiguration.class,
    MetricsAutoConfiguration.class
})
public class ObservabilityAutoConfiguration {
    
}
