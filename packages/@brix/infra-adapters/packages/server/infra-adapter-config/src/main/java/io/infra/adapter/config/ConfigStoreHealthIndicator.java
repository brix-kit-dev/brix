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
package io.infra.adapter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;

/**
 * Actuator health indicator for the Configuration Store capability.
 *
 * <p>Reports the state of configuration sources so operators can verify that
 * the expected property sources (YAML, env vars, cloud config) are loaded.
 * This is critical for diagnosing deployment issues where configuration files
 * may be missing or misconfigured.</p>
 *
 * <h3>Health Response Example</h3>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "details": {
 *     "activeProfiles": ["production", "postgresql"],
 *     "propertySourceCount": 7,
 *     "propertySources": [
 *       "configurationProperties",
 *       "systemEnvironment",
 *       "systemProperties",
 *       "applicationConfig: [classpath:/application.yml]",
 *       "applicationConfig: [classpath:/application-production.yml]"
 *     ]
 *   }
 * }
 * }</pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 * @see HealthIndicator
 */
public class ConfigStoreHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ConfigStoreHealthIndicator.class);

    private final Environment environment;

    /**
     * Constructs the health indicator with the given Spring {@link Environment}.
     *
     * @param environment the Spring Environment to inspect
     */
    public ConfigStoreHealthIndicator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Builds the health status for the configuration store.
     *
     * <p>Always returns {@code UP} because the Spring Environment is always available.
     * The real value is in the details, which expose the list of property sources
     * and active profiles for operational visibility.</p>
     *
     * @return the health status with configuration source details
     */
    @Override
    public Health health() {
        try {
            List<String> sourceNames = new ArrayList<>();

            if (environment instanceof ConfigurableEnvironment configurableEnv) {
                for (PropertySource<?> source : configurableEnv.getPropertySources()) {
                    sourceNames.add(source.getName());
                }
            }

            return Health.up()
                .withDetail("activeProfiles", environment.getActiveProfiles())
                .withDetail("propertySourceCount", sourceNames.size())
                .withDetail("propertySources", sourceNames)
                .build();
        } catch (Exception e) {
            log.error("[ConfigStoreHealth] Failed to collect property source information", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
