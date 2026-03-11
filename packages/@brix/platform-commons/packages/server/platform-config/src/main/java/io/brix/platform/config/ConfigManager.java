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
package io.brix.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Configuration manager.
 *
 * <p>Provides unified configuration management capabilities, including:
 * <ul>
 *   <li>Configuration retrieval</li>
 *   <li>Configuration change listening</li>
 *   <li>Configuration refresh</li>
 * </ul>
 *
 * @since 3.0.0
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private final ConfigProperties properties;
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Object>> listeners = new ConcurrentHashMap<>();

    public ConfigManager(ConfigProperties properties) {
        this.properties = properties;
    }

    /**
     * Get configuration value.
     *
     * @param key configuration key
     * @param <T> configuration value type
     * @return configuration value, returns null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key) {
        return (T) configCache.get(key);
    }

    /**
     * Get configuration value, returns default value if not found.
     *
     * @param key          configuration key
     * @param defaultValue default value
     * @param <T>          configuration value type
     * @return configuration value
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        Object value = configCache.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Set configuration value.
     *
     * @param key   configuration key
     * @param value configuration value
     */
    public void setConfig(String key, Object value) {
        Object oldValue = configCache.put(key, value);
        if (oldValue == null || !oldValue.equals(value)) {
            notifyListeners(key, value);
        }
    }

    /**
     * Register configuration change listener.
     *
     * @param key      configuration key
     * @param listener listener
     */
    public void addListener(String key, Consumer<Object> listener) {
        listeners.put(key, listener);
        log.debug("Registered configuration listener: {}", key);
    }

    /**
     * Remove configuration change listener.
     *
     * @param key configuration key
     */
    public void removeListener(String key) {
        listeners.remove(key);
        log.debug("Removed configuration listener: {}", key);
    }

    /**
     * Refresh all configurations.
     */
    public void refresh() {
        log.info("Refreshing all configurations...");
        // Subclasses or extensions can implement specific refresh logic
    }

    /**
     * Notify listeners.
     */
    private void notifyListeners(String key, Object value) {
        Consumer<Object> listener = listeners.get(key);
        if (listener != null) {
            try {
                listener.accept(value);
                log.debug("Configuration change notification succeeded: {} = {}", key, value);
            } catch (Exception e) {
                log.error("Configuration change notification failed: {} = {}", key, value, e);
            }
        }
    }

    public ConfigProperties getProperties() {
        return properties;
    }
}
