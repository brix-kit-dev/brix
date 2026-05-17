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

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import io.runtime.sdk.capability.ConfigNotFoundException;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Spring Environment-backed ConfigStoreCapability implementation.
 *
 * <p>This is the <b>production-grade</b> implementation of {@link ConfigStoreCapability}
 * that delegates all configuration resolution to the Spring {@link Environment} abstraction.
 * By leveraging Spring's property source hierarchy, this adapter automatically supports:</p>
 *
 * <ol>
 *   <li><b>Environment variables</b> — highest priority, following Spring relaxed binding
 *       (e.g., {@code BOOKING_MAX_DAYS_AHEAD} maps to {@code booking.max-days-ahead})</li>
 *   <li><b>System properties</b> — JVM {@code -D} flags</li>
 *   <li><b>application.yml / application.properties</b> — per active Spring profiles</li>
 *   <li><b>External config files</b> — via {@code spring.config.additional-location}</li>
 *   <li><b>Cloud config</b> — if Spring Cloud Config or Nacos/Consul bootstrap is on the classpath</li>
 * </ol>
 *
 * <h3>Key Design Decisions</h3>
 * <ul>
 *   <li>Relies on Spring's built-in relaxed binding — no custom env-var name mapping logic
 *       is needed because Spring Environment already resolves
 *       {@code booking.max-days-ahead}, {@code BOOKING_MAX_DAYS_AHEAD}, and
 *       {@code booking.maxDaysAhead} to the same property.</li>
 *   <li>Type conversion is delegated to {@link Environment#getProperty(String, Class)},
 *       which uses Spring's {@code ConversionService}. This covers all primitive types,
 *       enums, Duration, and custom converters registered in the application context.</li>
 *   <li>Thread-safe by design — {@link Environment} is inherently thread-safe in Spring Boot.</li>
 * </ul>
 *
 * <h3>Configuration Key Convention</h3>
 * <p>Plugin configuration keys should follow the namespace pattern:
 * {@code {plugin-name}.{category}.{property-name}}</p>
 * <pre>{@code
 * booking.max-days-ahead=30
 * identity.jwt.expire-seconds=3600
 * notification.email.smtp-host=mail.example.com
 * }</pre>
 *
 * <h3>Priority vs. Fallback Adapter</h3>
 * <p>This adapter is registered with {@link CapabilityLevel#STANDARD} priority and will
 * automatically supersede the {@code FallbackConfigStoreCapability} (which only reads
 * raw {@code System.getProperty} / {@code System.getenv} without Spring relaxed binding).
 * When this adapter is on the classpath, the fallback is not instantiated thanks to
 * {@code @ConditionalOnMissingBean} on the fallback registration.</p>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 * @see ConfigStoreCapability
 * @see Environment
 */
@Capability(
    type = ConfigStoreCapability.class,
    name = "spring-environment-config-store",
    description = "Production-grade ConfigStoreCapability backed by Spring Environment — "
        + "supports env vars, system properties, YAML, profiles, and cloud config",
    level = CapabilityLevel.STANDARD,
    priority = 100,
    aliases = {"springConfigStore", "environmentConfigStore"}
)
public class SpringEnvironmentConfigStoreCapability implements ConfigStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(SpringEnvironmentConfigStoreCapability.class);

    /**
     * Spring Environment instance providing unified access to all property sources
     * (env vars, system properties, YAML files, cloud config, etc.).
     */
    private final Environment environment;

    /**
     * Constructs a new adapter backed by the given Spring {@link Environment}.
     *
     * @param environment the Spring Environment to delegate all property lookups to;
     *                    must not be null
     * @throws NullPointerException if {@code environment} is null
     */
    public SpringEnvironmentConfigStoreCapability(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "Spring Environment must not be null");
        log.info("[ConfigStoreCapability] Spring Environment-backed config store initialized "
            + "with active profiles: {}", String.join(", ", environment.getActiveProfiles()));
    }

    /**
     * Retrieves a typed configuration value from the Spring Environment.
     *
     * <p>Resolution order follows the standard Spring property source hierarchy:</p>
     * <ol>
     *   <li>Environment variables (e.g., {@code BOOKING_MAX_DAYS_AHEAD})</li>
     *   <li>System properties (e.g., {@code -Dbooking.max-days-ahead=30})</li>
     *   <li>{@code application-{profile}.yml}</li>
     *   <li>{@code application.yml}</li>
     *   <li>Default properties</li>
     * </ol>
     *
     * <p>Type conversion is handled by Spring's {@code ConversionService}, supporting
     * all primitive wrappers, enums, {@code java.time.Duration}, and any custom converter
     * registered in the application context.</p>
     *
     * @param key  the property key using dot-separated namespace (e.g., {@code booking.max-days-ahead})
     * @param type the expected value type
     * @param <T>  the type parameter
     * @return an {@link Optional} containing the resolved value, or empty if the key is not found
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            T value = environment.getProperty(key, type);
            if (value != null) {
                return Optional.of(value);
            }
            log.trace("[ConfigStore] Key '{}' not found in any property source", key);
            return Optional.empty();
        } catch (Exception e) {
            // ConversionFailedException, IllegalStateException from Spring ConversionService
            log.warn("[ConfigStore] Failed to resolve key '{}' as type {}: {}",
                key, type.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Checks whether a property key exists in any of the Spring property sources.
     *
     * @param key the property key
     * @return {@code true} if at least one property source contains the key
     */
    @Override
    public boolean exists(String key) {
        return environment.containsProperty(key);
    }

    /**
     * Retrieves a required configuration value, throwing {@link ConfigNotFoundException}
     * if the key is absent.
     *
     * <p>This override provides a more descriptive error message that includes the
     * active Spring profiles, which helps operators diagnose missing configuration.</p>
     *
     * @param key  the property key
     * @param type the expected value type
     * @param <T>  the type parameter
     * @return the resolved value (never null)
     * @throws ConfigNotFoundException if the key is not found in any property source
     */
    @Override
    public <T> T getRequired(String key, Class<T> type) {
        return get(key, type).orElseThrow(() ->
            new ConfigNotFoundException(String.format(
                "Required config '%s' not found in any property source. "
                    + "Active profiles: [%s]. Check environment variables, "
                    + "application.yml, or external config.",
                key, String.join(", ", environment.getActiveProfiles())
            ))
        );
    }
}
