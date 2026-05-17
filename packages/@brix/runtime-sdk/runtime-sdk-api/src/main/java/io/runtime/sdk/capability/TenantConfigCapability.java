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

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import io.runtime.sdk.annotation.Since;

/**
 * Tenant Configuration Capability Contract — tenant-level configuration
 * with three-layer merge semantics.
 *
 * <p>This capability provides plugins with access to tenant configuration
 * resolved through a three-layer priority model:</p>
 * <ol>
 *   <li><b>User Preference</b> (highest) — per-user per-tenant overrides stored in biz_user_profile.preferences</li>
 *   <li><b>Tenant Config</b> — tenant admin settings from sys_tenant + sys_tenant_config</li>
 *   <li><b>Platform Default</b> (lowest) — global platform defaults from application.yml</li>
 * </ol>
 *
 * <p>Resolution rule: {@code effectiveValue = userPreference ?? tenantConfig ?? platformDefault}</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2A: Capability Contract (runtime-sdk-api). Implementations reside
 * in Layer 2C (platform-tenant). This interface contains <b>no infrastructure dependencies</b>.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private TenantConfigCapability tenantConfig;
 *
 * public LocalDate formatReportDate(LocalDate date) {
 *     Locale locale = tenantConfig.getEffectiveLocale();
 *     ZoneId zone = tenantConfig.getEffectiveTimezone();
 *     return date.atStartOfDay(zone).toLocalDate();
 * }
 *
 * public int getDefaultDuration() {
 *     return tenantConfig.getEffectiveConfig(
 *         "reservation.defaultDuration", Integer.class, 30);
 * }
 * }</pre>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Tenant ID is derived from authenticated context, never from user input</li>
 *   <li>Sensitive config values (is_sensitive=true) are masked in API responses</li>
 *   <li>Read-only config values can only be modified by platform admins</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.1.0
 * @see TenantCapability
 * @see ConfigStoreCapability
 */
@Since("3.1.0")
public interface TenantConfigCapability {

    /**
     * Gets the effective configuration value by merging all three layers.
     *
     * <p>Resolution order: user preference → tenant config → platform default.
     * Returns {@code null} if the key is not found in any layer.</p>
     *
     * @param key  configuration key (e.g. "defaultLocale", "reservation.defaultDuration")
     * @param type value type class
     * @param <T>  type parameter
     * @return the effective value, or {@code null} if not found
     */
    <T> T getEffectiveConfig(String key, Class<T> type);

    /**
     * Gets the effective configuration value with a fallback default.
     *
     * @param key          configuration key
     * @param type         value type class
     * @param defaultValue fallback value if not found in any layer
     * @param <T>          type parameter
     * @return the effective value, or {@code defaultValue} if not found
     */
    <T> T getEffectiveConfig(String key, Class<T> type, T defaultValue);

    /**
     * Gets a tenant-level configuration value (ignoring user preferences).
     *
     * @param namespace configuration namespace (e.g. "platform", "reservation")
     * @param key       configuration key within the namespace
     * @param type      value type class
     * @param <T>       type parameter
     * @return the tenant config value, or {@code null} if not set
     */
    <T> T getTenantConfig(String namespace, String key, Class<T> type);

    /**
     * Gets all configuration entries for a given namespace.
     *
     * @param namespace the configuration namespace
     * @return map of key→value for all entries in the namespace
     */
    Map<String, Object> getTenantConfigs(String namespace);

    /**
     * Gets the current tenant's settings snapshot.
     *
     * <p>Returns the tenant-level settings (sys_tenant columns) as a map.
     * This does not include user preference overrides.</p>
     *
     * @return tenant settings as key-value map
     */
    Map<String, Object> getTenantSettings();

    /**
     * Gets the effective locale (merged from all three layers).
     *
     * @return effective locale for the current user in the current tenant
     */
    Locale getEffectiveLocale();

    /**
     * Gets the effective timezone (merged from all three layers).
     *
     * @return effective timezone for the current user in the current tenant
     */
    ZoneId getEffectiveTimezone();

    /**
     * Gets the effective theme (merged from all three layers).
     *
     * @return effective theme name ("light", "dark", or "system")
     */
    String getEffectiveTheme();

    /**
     * Registers a listener for configuration changes within a namespace.
     *
     * <p>Called when a configuration value in the specified namespace is
     * created, updated, or deleted.</p>
     *
     * @param namespace the namespace to watch
     * @param listener  callback invoked on config changes
     */
    void onConfigChange(String namespace, Consumer<ConfigChangeEvent> listener);

    /**
     * Event payload for configuration change notifications.
     */
    record ConfigChangeEvent(
        String namespace,
        String key,
        Object oldValue,
        Object newValue,
        ChangeType changeType
    ) {
        public enum ChangeType { CREATED, UPDATED, DELETED }
    }
}
