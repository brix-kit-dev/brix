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

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Interface for plugin-specific health indicators.
 *
 * <p>Plugins implement this interface to contribute their own health status
 * to the composite health endpoint. Each plugin's health indicator is
 * automatically discovered and aggregated by
 * {@link CompositePluginHealthAggregator}.</p>
 *
 * <h3>Architecture Compliance</h3>
 * <p>This interface resides in platform-observability (Layer 2C).
 * Plugin implementations register as Spring beans and are auto-discovered
 * via dependency injection, following the Netflix Eureka-style composite
 * health pattern referenced in the architecture review.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Component
 * public class BookingPluginHealthIndicator implements PluginHealthIndicator {
 *
 *     @Override
 *     public String getPluginName() {
 *         return "app-booking";
 *     }
 *
 *     @Override
 *     public Health health() {
 *         // Check plugin-specific health (e.g., external API connectivity)
 *         return Health.up()
 *                 .withDetail("bookingsProcessed", 42)
 *                 .build();
 *     }
 * }
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see CompositePluginHealthAggregator
 */
public interface PluginHealthIndicator extends HealthIndicator {

    /**
     * Returns the unique name of the plugin contributing this health indicator.
     *
     * <p>This name is used as the key in the composite health response,
     * e.g., {@code "plugins.app-booking": {"status": "UP"}}.</p>
     *
     * @return the plugin name, must be non-null and unique across all plugins
     */
    String getPluginName();
}
