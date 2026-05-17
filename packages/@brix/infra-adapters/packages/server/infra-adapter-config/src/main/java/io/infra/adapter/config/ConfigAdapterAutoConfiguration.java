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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import io.runtime.sdk.capability.ConfigStoreCapability;

/**
 * Spring Boot auto-configuration for the Config Store adapter.
 *
 * <p>This auto-configuration registers a production-grade
 * {@link SpringEnvironmentConfigStoreCapability} backed by the Spring {@link Environment}.
 * It supersedes the fallback implementation from {@code infra-adapter-fallback} by virtue of
 * {@code @ConditionalOnMissingBean} on the fallback side — when this module is on the
 * classpath and enabled, the fallback bean is not created.</p>
 *
 * <h3>Activation Conditions</h3>
 * <ul>
 *   <li>{@code brix.infra.config.enabled=true} (default: {@code true})</li>
 *   <li>{@link ConfigStoreCapability} class must be on the classpath
 *       (i.e., runtime-sdk-api must be a dependency)</li>
 *   <li>No other {@link ConfigStoreCapability} bean already registered</li>
 * </ul>
 *
 * <h3>Registered Beans</h3>
 * <ul>
 *   <li>{@link SpringEnvironmentConfigStoreCapability} — the primary capability bean</li>
 *   <li>{@link ConfigStoreHealthIndicator} — optional actuator health indicator
 *       (when {@code spring-boot-actuator} is on the classpath)</li>
 * </ul>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 */
@AutoConfiguration
@ConditionalOnClass(ConfigStoreCapability.class)
@ConditionalOnProperty(prefix = "brix.infra.config", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ConfigAdapterProperties.class)
public class ConfigAdapterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConfigAdapterAutoConfiguration.class);

    /**
     * Registers the Spring Environment-backed {@link ConfigStoreCapability} bean.
     *
     * <p>This bean is only created when no other {@link ConfigStoreCapability} is already
     * present in the application context, allowing downstream projects to provide a
     * custom implementation (e.g., Nacos, Consul) that takes precedence.</p>
     *
     * @param environment the Spring Environment injected by the container
     * @return a production-grade config store capability
     */
    @Bean
    @ConditionalOnMissingBean(ConfigStoreCapability.class)
    public ConfigStoreCapability springEnvironmentConfigStoreCapability(Environment environment) {
        log.info("[ConfigAdapterAutoConfiguration] Registering Spring Environment-backed "
            + "ConfigStoreCapability (active profiles: {})",
            String.join(", ", environment.getActiveProfiles()));
        return new SpringEnvironmentConfigStoreCapability(environment);
    }

    /**
     * Registers the config store health indicator for actuator integration.
     *
     * <p>Only created when:</p>
     * <ul>
     *   <li>{@code HealthIndicator} class is on the classpath (spring-boot-actuator)</li>
     *   <li>{@code brix.infra.config.health-indicator-enabled=true} (default)</li>
     * </ul>
     *
     * @param environment the Spring Environment to inspect
     * @param properties  the adapter configuration properties
     * @return the health indicator bean
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnProperty(prefix = "brix.infra.config", name = "health-indicator-enabled",
        havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ConfigStoreHealthIndicator.class)
    public ConfigStoreHealthIndicator configStoreHealthIndicator(
            Environment environment, ConfigAdapterProperties properties) {
        log.info("[ConfigAdapterAutoConfiguration] Registering ConfigStore health indicator");
        return new ConfigStoreHealthIndicator(environment);
    }
}
