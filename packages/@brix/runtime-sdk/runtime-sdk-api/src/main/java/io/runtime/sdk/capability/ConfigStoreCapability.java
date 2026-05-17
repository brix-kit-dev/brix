/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Configuration Store Capability Contract
 *
 * <p>Provides a unified interface for module configuration reading,
 * supporting multiple configuration sources (environment variables,
 * configuration center, local files). Modules use this interface to
 * retrieve configuration values without knowing the source or refresh mechanism.</p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Configuration reading: supports multiple data types</li>
 *   <li>Default value handling: returns default when config is missing</li>
 *   <li>Configuration refresh: supports runtime config updates (implemented by Host)</li>
 * </ul>
 *
 * <h3>Configuration Key Naming Convention</h3>
 * <p>Use dot-separated namespaces: {module}.{category}.{name}</p>
 * <pre>{@code
 * // Examples
 * "booking.max-days-ahead"
 * "identity.jwt.expire-seconds"
 * "notification.email.smtp-host"
 * }</pre>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private ConfigStoreCapability configStore;
 *
 * public void configure() {
 *     // Get string configuration
 *     String host = configStore.getString("notification.email.smtp-host", "localhost");
 *
 *     // Get integer configuration
 *     int maxDays = configStore.getInt("booking.max-days-ahead", 30);
 *
 *     // Get boolean configuration
 *     boolean enabled = configStore.getBoolean("feature.new-ui.enabled", false);
 * }
 * }</pre>
 *
 * <h3>Implementation Notes</h3>
 * <ul>
 *   <li>Full Product Host: Nacos/Apollo configuration center</li>
 *   <li>Embedded Host: Environment variables + local YAML files</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Since("3.0.0")
public interface ConfigStoreCapability {

    /**
     * Get configuration value
     *
     * @param key  Configuration key
     * @param type Value type class
     * @param <T>  Type parameter
     * @return Configuration value, or {@link Optional#empty()} if not exists
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Get string configuration
     *
     * @param key          Configuration key
     * @param defaultValue Default value
     * @return Configuration value, or default if not exists
     */
    default String getString(String key, String defaultValue) {
        return get(key, String.class).orElse(defaultValue);
    }

    /**
     * Get string configuration (without default)
     *
     * @param key Configuration key
     * @return Configuration value as Optional
     */
    default Optional<String> getString(String key) {
        return get(key, String.class);
    }

    /**
     * Get integer configuration
     *
     * @param key          Configuration key
     * @param defaultValue Default value
     * @return Configuration value
     */
    default int getInt(String key, int defaultValue) {
        return get(key, Integer.class).orElse(defaultValue);
    }

    /**
     * Get long integer configuration
     *
     * @param key          Configuration key
     * @param defaultValue Default value
     * @return Configuration value
     */
    default long getLong(String key, long defaultValue) {
        return get(key, Long.class).orElse(defaultValue);
    }

    /**
     * Get double precision floating point configuration
     *
     * @param key          Configuration key
     * @param defaultValue Default value
     * @return Configuration value
     */
    default double getDouble(String key, double defaultValue) {
        return get(key, Double.class).orElse(defaultValue);
    }

    /**
     * Get boolean configuration
     *
     * @param key          Configuration key
     * @param defaultValue Default value
     * @return Configuration value
     */
    default boolean getBoolean(String key, boolean defaultValue) {
        return get(key, Boolean.class).orElse(defaultValue);
    }

    /**
     * Check if configuration exists
     *
     * @param key Configuration key
     * @return true if configuration exists
     */
    default boolean exists(String key) {
        return get(key, Object.class).isPresent();
    }

    /**
     * Get required configuration (throws exception if not exists)
     *
     * @param key  Configuration key
     * @param type Value type class
     * @param <T>  Type parameter
     * @return Configuration value
     * @throws ConfigNotFoundException if configuration not found
     */
    default <T> T getRequired(String key, Class<T> type) {
        return get(key, type).orElseThrow(() ->
            new ConfigNotFoundException("Required config not found: " + key));
    }

    /**
     * Get required string configuration (throws exception if not exists)
     *
     * <p>This is a convenience method for retrieving mandatory string configuration.
     * It is particularly useful for security-sensitive configurations like OAuth URLs,
     * API endpoints, and other values that should never have default localhost fallbacks.</p>
     *
     * <h4>Usage Example</h4>
     * <pre>{@code
     * // Instead of:
     * String url = configStore.getString("oauth.redirect-uri", "http://localhost:8080");
     *
     * // Use:
     * String url = configStore.getRequiredString("oauth.redirect-uri");
     * // Throws ConfigNotFoundException if not configured
     * }</pre>
     *
     * @param key Configuration key
     * @return Configuration value (never null)
     * @throws ConfigNotFoundException if configuration not found or empty
     */
    default String getRequiredString(String key) {
        String value = getRequired(key, String.class);
        if (value == null || value.isBlank()) {
            throw new ConfigNotFoundException("Required config is empty: " + key);
        }
        return value;
    }
}
