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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributor;

/**
 * Composite health aggregator that collects health status from all registered plugins.
 *
 * <p>This component implements the Netflix Eureka-style composite health pattern,
 * aggregating individual plugin health indicators into a single hierarchical health
 * endpoint. The aggregated result is exposed at {@code /actuator/health/plugins}.</p>
 *
 * <h3>Health Response Structure</h3>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "plugins": {
 *       "status": "UP",
 *       "components": {
 *         "app-booking": { "status": "UP", "details": { ... } },
 *         "app-identity": { "status": "UP", "details": { ... } }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h3>Aggregation Rules</h3>
 * <ul>
 *   <li>If ALL plugins are UP, composite status is UP</li>
 *   <li>If ANY plugin is DOWN, composite status is DOWN</li>
 *   <li>If a plugin health check throws an exception, that plugin reports DOWN
 *       with the exception detail, but other plugins are still checked</li>
 *   <li>If no plugins are registered, composite status is UP with an empty components list</li>
 * </ul>
 *
 * <h3>Architecture Compliance</h3>
 * <p>Resides in platform-observability (Layer 2C). Follows the Ultra-Thin Host
 * principle (Constraint 6): the Host layer only references this via YAML
 * configuration, containing zero health-check logic.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see PluginHealthIndicator
 * @see HealthCheckAutoConfiguration
 */
public class CompositePluginHealthAggregator implements CompositeHealthContributor {

    private static final Logger log = LoggerFactory.getLogger(CompositePluginHealthAggregator.class);

    /**
     * Immutable map of plugin name to health indicator.
     * Preserves insertion order for deterministic health report output.
     */
    private final Map<String, HealthIndicator> pluginHealthIndicators;

    /**
     * Creates the composite aggregator from discovered plugin health indicators.
     *
     * <p>Each plugin health indicator's name is extracted via
     * {@link PluginHealthIndicator#getPluginName()} and used as the key in the
     * composite health response. Duplicate plugin names are logged as warnings
     * and the last-registered indicator wins.</p>
     *
     * @param indicators list of plugin health indicators discovered via Spring DI;
     *                   may be empty if no plugins provide health indicators
     */
    public CompositePluginHealthAggregator(List<PluginHealthIndicator> indicators) {
        Map<String, HealthIndicator> map = new LinkedHashMap<>();
        for (PluginHealthIndicator indicator : indicators) {
            String name = indicator.getPluginName();
            if (map.containsKey(name)) {
                log.warn("[CompositeHealth] Duplicate plugin health indicator name '{}', "
                        + "last registration wins", name);
            }
            map.put(name, indicator);
        }
        this.pluginHealthIndicators = Collections.unmodifiableMap(map);

        log.info("[CompositeHealth] Plugin health aggregator initialized with {} plugin(s): {}",
                pluginHealthIndicators.size(), pluginHealthIndicators.keySet());
    }

    /**
     * Returns a specific plugin's health contributor by name.
     *
     * @param name the plugin name
     * @return the health contributor, or {@code null} if no plugin with that name exists
     */
    @Override
    public HealthContributor getContributor(String name) {
        return pluginHealthIndicators.get(name);
    }

    /**
     * Returns an iterator over all plugin health contributors.
     *
     * <p>Each entry pairs a plugin name with its health indicator, enabling
     * Spring Boot Actuator to build the composite health tree.</p>
     *
     * @return iterator of named health contributors
     */
    @Override
    public Iterator<NamedContributor<HealthContributor>> iterator() {
        return pluginHealthIndicators.entrySet().stream()
                .<NamedContributor<HealthContributor>>map(entry -> NamedContributor.of(entry.getKey(), entry.getValue()))
                .iterator();
    }

    /**
     * Performs a synchronous health check across all registered plugins.
     *
     * <p>This method is provided for programmatic access outside Spring Boot
     * Actuator's built-in aggregation. Each plugin is checked independently;
     * failures in one plugin do not prevent other plugins from being checked.</p>
     *
     * @return a map of plugin name to Health status
     */
    public Map<String, Health> checkAllPlugins() {
        Map<String, Health> results = new LinkedHashMap<>();
        for (Map.Entry<String, HealthIndicator> entry : pluginHealthIndicators.entrySet()) {
            String pluginName = entry.getKey();
            try {
                Health health = entry.getValue().health();
                results.put(pluginName, health);
            } catch (Exception e) {
                log.error("[CompositeHealth] Health check failed for plugin '{}': {}",
                        pluginName, e.getMessage(), e);
                results.put(pluginName, Health.down(e)
                        .withDetail("error", e.getMessage())
                        .build());
            }
        }
        return results;
    }
}
