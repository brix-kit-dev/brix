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
package io.brix.platform.tenant.service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.dto.EffectiveConfigDto;
import io.brix.platform.tenant.entity.TenantConfig;
import io.runtime.sdk.capability.TenantConfigCapability;

/**
 * Layer 2C implementation of {@link TenantConfigCapability}.
 *
 * <p>Bridges the SDK capability contract to the platform-tenant
 * {@link TenantSettingsService}. Resolves the current tenant and user
 * from {@link TenantContext} ThreadLocal for implicit context access.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — capability implementation bridging
 * Layer 2A contract to platform infrastructure.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This implementation is thread-safe. Config change listeners are stored
 * in a ConcurrentHashMap. The underlying TenantSettingsService is transactional.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantConfigCapability
 * @see TenantSettingsService
 */
public class TenantConfigCapabilityImpl implements TenantConfigCapability {

    private static final Logger log = LoggerFactory.getLogger(TenantConfigCapabilityImpl.class);

    private final TenantSettingsService settingsService;
    private final ObjectMapper objectMapper;

    private final Map<String, List<Consumer<ConfigChangeEvent>>> listeners = new ConcurrentHashMap<>();

    public TenantConfigCapabilityImpl(TenantSettingsService settingsService,
                                      ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T getEffectiveConfig(String key, Class<T> type) {
        return getEffectiveConfig(key, type, null);
    }

    @Override
    public <T> T getEffectiveConfig(String key, Class<T> type, T defaultValue) {
        Long tenantId = resolveCurrentTenantId();
        Long userId = resolveCurrentUserId();

        if (tenantId == null) {
            log.debug("No tenant context, returning default for key={}", key);
            return defaultValue;
        }

        EffectiveConfigDto effective = settingsService.getEffectiveConfig(tenantId, userId);
        Object raw = resolveEffectiveField(effective, key);

        if (raw == null) {
            // Fall back to namespace config: check "platform" namespace
            List<TenantConfig> configs = settingsService.getNamespaceConfigs(tenantId, "platform");
            for (TenantConfig cfg : configs) {
                if (cfg.getConfigKey().equals(key)) {
                    return convertValue(cfg.getConfigValue(), type, defaultValue);
                }
            }
            return defaultValue;
        }

        return convertValue(String.valueOf(raw), type, defaultValue);
    }

    @Override
    public <T> T getTenantConfig(String namespace, String key, Class<T> type) {
        Long tenantId = resolveCurrentTenantId();
        if (tenantId == null) {
            return null;
        }

        List<TenantConfig> configs = settingsService.getNamespaceConfigs(tenantId, namespace);
        for (TenantConfig cfg : configs) {
            if (cfg.getConfigKey().equals(key)) {
                return convertValue(cfg.getConfigValue(), type, null);
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> getTenantConfigs(String namespace) {
        Long tenantId = resolveCurrentTenantId();
        if (tenantId == null) {
            return Map.of();
        }

        List<TenantConfig> configs = settingsService.getNamespaceConfigs(tenantId, namespace);
        Map<String, Object> result = new LinkedHashMap<>();
        for (TenantConfig cfg : configs) {
            if (cfg.isSensitive()) {
                result.put(cfg.getConfigKey(), "***");
            } else {
                result.put(cfg.getConfigKey(), parseJsonValue(cfg.getConfigValue()));
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> getTenantSettings() {
        Long tenantId = resolveCurrentTenantId();
        if (tenantId == null) {
            return Map.of();
        }

        EffectiveConfigDto effective = settingsService.getEffectiveConfig(tenantId, null);
        return effective.toMap();
    }

    @Override
    public Locale getEffectiveLocale() {
        Long tenantId = resolveCurrentTenantId();
        Long userId = resolveCurrentUserId();

        if (tenantId == null) {
            return Locale.getDefault();
        }

        EffectiveConfigDto effective = settingsService.getEffectiveConfig(tenantId, userId);
        String locale = effective.getLocale();
        if (locale == null || locale.isBlank()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(locale.replace('_', '-'));
    }

    @Override
    public ZoneId getEffectiveTimezone() {
        Long tenantId = resolveCurrentTenantId();
        Long userId = resolveCurrentUserId();

        if (tenantId == null) {
            return ZoneId.systemDefault();
        }

        EffectiveConfigDto effective = settingsService.getEffectiveConfig(tenantId, userId);
        String tz = effective.getTimezone();
        if (tz == null || tz.isBlank()) {
            return ZoneId.systemDefault();
        }
        return ZoneId.of(tz);
    }

    @Override
    public String getEffectiveTheme() {
        Long tenantId = resolveCurrentTenantId();
        Long userId = resolveCurrentUserId();

        if (tenantId == null) {
            return "light";
        }

        EffectiveConfigDto effective = settingsService.getEffectiveConfig(tenantId, userId);
        String theme = effective.getTheme();
        return (theme != null && !theme.isBlank()) ? theme : "light";
    }

    @Override
    public void onConfigChange(String namespace, Consumer<ConfigChangeEvent> listener) {
        listeners.computeIfAbsent(namespace, k -> new ArrayList<>()).add(listener);
        log.debug("Registered config change listener for namespace={}", namespace);
    }

    /**
     * Fires a config change event to all registered listeners for the given namespace.
     *
     * <p>Called internally when config values are modified via the service layer.</p>
     *
     * @param event the change event
     */
    public void fireConfigChangeEvent(ConfigChangeEvent event) {
        List<Consumer<ConfigChangeEvent>> namespaceListeners = listeners.get(event.namespace());
        if (namespaceListeners != null) {
            for (Consumer<ConfigChangeEvent> listener : namespaceListeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    log.error("Config change listener error for namespace={}: {}",
                            event.namespace(), e.getMessage(), e);
                }
            }
        }
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private Long resolveCurrentTenantId() {
        return TenantContext.getTenantId()
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        log.debug("Non-numeric tenant ID in context: {}", s);
                        return null;
                    }
                })
                .orElse(null);
    }

    private Long resolveCurrentUserId() {
        return TenantContext.getUserId()
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        log.debug("Non-numeric user ID in context: {}", s);
                        return null;
                    }
                })
                .orElse(null);
    }

    private Object resolveEffectiveField(EffectiveConfigDto dto, String key) {
        return switch (key) {
            case "defaultLocale", "locale" -> dto.getLocale();
            case "defaultTimezone", "timezone" -> dto.getTimezone();
            case "defaultDateFormat", "dateFormat" -> dto.getDateFormat();
            case "defaultTimeFormat", "timeFormat" -> dto.getTimeFormat();
            case "defaultCurrency", "currency" -> dto.getCurrency();
            case "defaultTheme", "theme" -> dto.getTheme();
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T convertValue(String raw, Class<T> type, T defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            if (type == String.class) {
                // Strip JSON quotes if present
                if (raw.startsWith("\"") && raw.endsWith("\"")) {
                    return (T) raw.substring(1, raw.length() - 1);
                }
                return (T) raw;
            }
            if (type == Integer.class || type == int.class) {
                return (T) Integer.valueOf(raw.trim());
            }
            if (type == Long.class || type == long.class) {
                return (T) Long.valueOf(raw.trim());
            }
            if (type == Boolean.class || type == boolean.class) {
                return (T) Boolean.valueOf(raw.trim());
            }
            if (type == Double.class || type == double.class) {
                return (T) Double.valueOf(raw.trim());
            }
            // Complex types: delegate to ObjectMapper
            return objectMapper.readValue(raw, type);
        } catch (JsonProcessingException | NumberFormatException e) {
            log.warn("Failed to convert config value '{}' to {}: {}", raw, type.getSimpleName(), e.getMessage());
            return defaultValue;
        }
    }

    private Object parseJsonValue(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
