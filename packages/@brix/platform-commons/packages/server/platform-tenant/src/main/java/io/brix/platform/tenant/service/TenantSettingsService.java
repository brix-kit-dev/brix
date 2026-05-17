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

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import io.brix.platform.tenant.dto.EffectiveConfigDto;
import io.brix.platform.tenant.dto.TenantBrandingDto;
import io.brix.platform.tenant.dto.TenantConfigDto;
import io.brix.platform.tenant.dto.TenantSettingsDto;
import io.brix.platform.tenant.dto.UserPreferencesDto;
import io.brix.platform.tenant.entity.TenantConfig;

/**
 * Service interface for tenant settings, user preferences, branding,
 * and plugin-level namespace configuration.
 *
 * <p>Implements the three-layer configuration merge model:
 * {@code effectiveValue = userPreference ?? tenantConfig ?? platformDefault}</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — business logic for tenant configuration.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public interface TenantSettingsService {

    // ========================================================================
    // Tenant Settings (admin)
    // ========================================================================

    /**
     * Gets the current tenant's settings.
     *
     * @param tenantId the tenant ID
     * @return tenant settings DTO
     */
    TenantSettingsDto getTenantSettings(Long tenantId);

    /**
     * Partially updates tenant settings (PATCH semantics).
     *
     * @param tenantId the tenant ID
     * @param dto      fields to update (non-null fields only)
     */
    void updateTenantSettings(Long tenantId, TenantSettingsDto dto);

    // ========================================================================
    // Three-Layer Merge
    // ========================================================================

    /**
     * Computes effective configuration by merging:
     * user preferences → tenant settings → platform defaults.
     *
     * @param tenantId the tenant ID
     * @param userId   the user profile ID (biz_user_profile.id), may be null
     * @return effective config with source annotations
     */
    EffectiveConfigDto getEffectiveConfig(Long tenantId, Long userId);

    // ========================================================================
    // User Preferences
    // ========================================================================

    /**
     * Gets user preferences from biz_user_profile.preferences.
     *
     * @param userId the user profile ID
     * @return user preferences DTO
     */
    UserPreferencesDto getUserPreferences(Long userId);

    /**
     * Partially updates user preferences (PATCH semantics).
     *
     * @param userId the user profile ID
     * @param dto    fields to update (non-null fields only)
     */
    void updateUserPreferences(Long userId, UserPreferencesDto dto);

    // ========================================================================
    // Branding
    // ========================================================================

    /**
     * Gets tenant branding configuration.
     *
     * @param tenantId the tenant ID
     * @return branding DTO
     */
    TenantBrandingDto getBranding(Long tenantId);

    /**
     * Updates tenant branding configuration.
     *
     * @param tenantId the tenant ID
     * @param dto      branding fields to update
     */
    void updateBranding(Long tenantId, TenantBrandingDto dto);

    /**
     * Uploads a logo image for the tenant and persists the URL.
     *
     * <p>The implementation should store the file (e.g., local filesystem or CDN)
     * and update {@code sys_tenant.logo_url} with the resulting URL.</p>
     *
     * @param tenantId the tenant ID
     * @param file     the uploaded logo file (already validated for size and type)
     * @return the persisted logo URL
     */
    String uploadLogo(Long tenantId, MultipartFile file);

    // ========================================================================
    // Namespace Config (plugin-level)
    // ========================================================================

    /**
     * Gets all config entries for a given namespace.
     *
     * @param tenantId  the tenant ID
     * @param namespace the config namespace
     * @return list of config entries
     */
    List<TenantConfig> getNamespaceConfigs(Long tenantId, String namespace);

    /**
     * Creates or updates a config entry.
     *
     * @param tenantId  the tenant ID
     * @param namespace the config namespace
     * @param key       the config key
     * @param dto       the config value and metadata
     */
    void putConfig(Long tenantId, String namespace, String key, TenantConfigDto dto);

    /**
     * Deletes a config entry.
     *
     * @param tenantId  the tenant ID
     * @param namespace the config namespace
     * @param key       the config key
     */
    void deleteConfig(Long tenantId, String namespace, String key);
}
