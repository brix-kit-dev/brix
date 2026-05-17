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
package io.brix.platform.observability.health;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import io.brix.platform.observability.ObservabilityProperties;

/**
 * Health check auto-configuration.
 *
 * <p>Registers health indicators for infrastructure components (Redis, Database, etc.)
 * and assembles a composite plugin health aggregator that exposes all plugin health
 * statuses under {@code /actuator/health/plugins}.</p>
 *
 * <h3>Composite Health Endpoint</h3>
 * <p>The composite aggregator follows the Netflix Eureka pattern: individual plugins
 * implement {@link PluginHealthIndicator} and are auto-discovered via Spring DI.
 * The aggregator collects all plugin health statuses into a single hierarchical
 * response, enabling operators to monitor platform-wide health at a glance.</p>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * management:
 *   endpoint:
 *     health:
 *       show-details: always      # Show plugin-level details
 *       show-components: always   # Show component breakdown
 *
 * observability:
 *   health:
 *     composite:
 *       enabled: true             # Enable composite plugin health
 *     redis:
 *       enabled: true
 * }</pre>
 *
 * @author Brix Platform Team
 * @version 2.0.0 (Phase 5 — Composite Health)
 * @see CompositePluginHealthAggregator
 * @see PluginHealthIndicator
 */
@AutoConfiguration
public class HealthCheckAutoConfiguration {

    /**
     * Creates a composite health aggregator that collects health from all registered plugins.
     *
     * <p>Exposed at {@code /actuator/health/plugins}. If no plugins implement
     * {@link PluginHealthIndicator}, the aggregator reports UP with an empty
     * component list — this is safe for environments without plugin health indicators.</p>
     *
     * @param indicators all discovered plugin health indicators (may be empty)
     * @return composite plugin health aggregator
     */
    @Bean("plugins")
    @ConditionalOnProperty(prefix = "observability.health.composite", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public CompositePluginHealthAggregator compositePluginHealthAggregator(
            List<PluginHealthIndicator> indicators) {
        return new CompositePluginHealthAggregator(
                indicators != null ? indicators : Collections.emptyList());
    }

    /**
     * Redis health indicator.
     *
     * <p>Reports Redis connection status and response time at
     * {@code /actuator/health/redis}. Activates only when Spring Data Redis
     * is on the classpath and a {@link RedisConnectionFactory} bean exists.</p>
     *
     * @param connectionFactory Redis connection factory
     * @param properties        observability configuration properties
     * @return Redis health indicator
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "observability.health.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisHealthIndicator redisHealthIndicator(
            RedisConnectionFactory connectionFactory,
            ObservabilityProperties properties) {
        return new RedisHealthIndicator(connectionFactory, 
                properties.getHealth().getRedis().getTimeoutMs());
    }
}
