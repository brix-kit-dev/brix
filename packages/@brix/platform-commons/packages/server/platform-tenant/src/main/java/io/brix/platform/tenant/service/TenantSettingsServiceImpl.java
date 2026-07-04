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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.EffectiveConfigDto;
import io.brix.platform.tenant.dto.TenantBrandingDto;
import io.brix.platform.tenant.dto.TenantConfigDto;
import io.brix.platform.tenant.dto.TenantSettingsDto;
import io.brix.platform.tenant.dto.UserPreferencesDto;
import io.brix.platform.tenant.entity.BizUserProfile;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantConfig;
import io.brix.platform.tenant.enums.MfaPolicy;
import io.brix.platform.tenant.enums.ThemeMode;
import io.brix.platform.tenant.repository.BizUserProfileRepository;
import io.brix.platform.tenant.repository.TenantConfigRepository;
import io.brix.platform.tenant.repository.TenantRepository;

/**
 * Implementation of TenantSettingsService with three-layer configuration merge.
 *
 * <p>Resolution rule: {@code effectiveValue = userPreference ?? tenantConfig ?? platformDefault}</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — multi-tenant configuration capability implementation.</p>
 *
 * <h3>Platform Defaults</h3>
 * <p>Platform-level defaults are loaded from {@code application.yml} properties
 * with prefix {@code brix.tenant.defaults.*}. If not configured, sensible
 * built-in defaults are used (zh-CN, UTC, etc.).</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@Service
public class TenantSettingsServiceImpl implements TenantSettingsService {

    private static final Logger log = LoggerFactory.getLogger(TenantSettingsServiceImpl.class);

    private final TenantRepository tenantRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final BizUserProfileRepository bizUserProfileRepository;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // Platform Defaults (Layer 3: lowest priority)
    // ========================================================================

    @Value("${brix.tenant.defaults.locale:zh-CN}")
    private String platformDefaultLocale;

    @Value("${brix.tenant.defaults.timezone:UTC}")
    private String platformDefaultTimezone;

    @Value("${brix.tenant.defaults.date-format:YYYY-MM-DD}")
    private String platformDefaultDateFormat;

    @Value("${brix.tenant.defaults.time-format:24h}")
    private String platformDefaultTimeFormat;

    @Value("${brix.tenant.defaults.currency:CNY}")
    private String platformDefaultCurrency;

    @Value("${brix.tenant.defaults.theme:LIGHT}")
    private String platformDefaultTheme;

    @Value("${brix.tenant.branding.upload-dir:uploads/branding}")
    private String brandingUploadDir;

    @Value("${brix.tenant.branding.base-url:/uploads/branding}")
    private String brandingBaseUrl;

    public TenantSettingsServiceImpl(TenantRepository tenantRepository,
                                     TenantConfigRepository tenantConfigRepository,
                                     BizUserProfileRepository bizUserProfileRepository,
                                     IdGenerator idGenerator,
                                     ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantConfigRepository = tenantConfigRepository;
        this.bizUserProfileRepository = bizUserProfileRepository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    // ========================================================================
    // Tenant Settings
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public TenantSettingsDto getTenantSettings(Long tenantId) {
        Tenant tenant = findTenant(tenantId);

        TenantSettingsDto dto = new TenantSettingsDto();
        dto.setDefaultLocale(tenant.getDefaultLocale());
        dto.setDefaultTimezone(tenant.getDefaultTimezone());
        dto.setDefaultDateFormat(tenant.getDefaultDateFormat());
        dto.setDefaultTimeFormat(tenant.getDefaultTimeFormat());
        dto.setDefaultCurrency(tenant.getDefaultCurrency());
        dto.setDefaultTheme(tenant.getDefaultTheme() != null ? tenant.getDefaultTheme().name() : null);
        dto.setSessionTimeoutMinutes(tenant.getSessionTimeoutMinutes());
        dto.setMfaPolicy(tenant.getMfaPolicy() != null ? tenant.getMfaPolicy().name() : null);
        dto.setAllowedLoginMethods(tenant.getAllowedLoginMethods());
        dto.setPasswordPolicy(tenant.getPasswordPolicy());
        dto.setNotificationChannels(tenant.getNotificationChannels());
        dto.setBusinessHours(tenant.getBusinessHours());
        return dto;
    }

    @Override
    @Transactional
    public void updateTenantSettings(Long tenantId, TenantSettingsDto dto) {
        Tenant tenant = findTenant(tenantId);

        // PATCH semantics: only update non-null fields
        if (dto.getDefaultLocale() != null) tenant.setDefaultLocale(dto.getDefaultLocale());
        if (dto.getDefaultTimezone() != null) tenant.setDefaultTimezone(dto.getDefaultTimezone());
        if (dto.getDefaultDateFormat() != null) tenant.setDefaultDateFormat(dto.getDefaultDateFormat());
        if (dto.getDefaultTimeFormat() != null) tenant.setDefaultTimeFormat(dto.getDefaultTimeFormat());
        if (dto.getDefaultCurrency() != null) tenant.setDefaultCurrency(dto.getDefaultCurrency());
        if (dto.getDefaultTheme() != null) tenant.setDefaultTheme(ThemeMode.valueOf(dto.getDefaultTheme()));
        if (dto.getSessionTimeoutMinutes() != null) tenant.setSessionTimeoutMinutes(dto.getSessionTimeoutMinutes());
        if (dto.getMfaPolicy() != null) tenant.setMfaPolicy(MfaPolicy.valueOf(dto.getMfaPolicy()));
        if (dto.getAllowedLoginMethods() != null) tenant.setAllowedLoginMethods(dto.getAllowedLoginMethods());
        if (dto.getPasswordPolicy() != null) tenant.setPasswordPolicy(dto.getPasswordPolicy());
        if (dto.getNotificationChannels() != null) tenant.setNotificationChannels(dto.getNotificationChannels());
        if (dto.getBusinessHours() != null) tenant.setBusinessHours(dto.getBusinessHours());

        tenantRepository.save(tenant);
        log.info("Updated tenant settings for tenantId={}", tenantId);
    }

    // ========================================================================
    // Three-Layer Merge
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public EffectiveConfigDto getEffectiveConfig(Long tenantId, Long userId) {
        Tenant tenant = findTenant(tenantId);
        Map<String, String> userPrefs = loadUserPreferences(userId);

        EffectiveConfigDto dto = new EffectiveConfigDto();

        // Locale: user → tenant → platform
        String tenantLocale = tenant.getDefaultLocale();
        resolveField(dto, "locale",
                userPrefs.get("locale"), tenantLocale, platformDefaultLocale);

        // Timezone
        String tenantTimezone = tenant.getDefaultTimezone();
        resolveField(dto, "timezone",
                userPrefs.get("timezone"), tenantTimezone, platformDefaultTimezone);

        // Date format
        String tenantDateFormat = tenant.getDefaultDateFormat();
        resolveField(dto, "dateFormat",
                userPrefs.get("dateFormat"), tenantDateFormat, platformDefaultDateFormat);

        // Time format
        String tenantTimeFormat = tenant.getDefaultTimeFormat();
        resolveField(dto, "timeFormat",
                userPrefs.get("timeFormat"), tenantTimeFormat, platformDefaultTimeFormat);

        // Currency
        String tenantCurrency = tenant.getDefaultCurrency();
        resolveField(dto, "currency",
                userPrefs.get("currency"), tenantCurrency, platformDefaultCurrency);

        // Theme
        String tenantTheme = tenant.getDefaultTheme() != null ? tenant.getDefaultTheme().name().toLowerCase() : null;
        resolveField(dto, "theme",
                userPrefs.get("theme"), tenantTheme, platformDefaultTheme.toLowerCase());

        return dto;
    }

    // ========================================================================
    // User Preferences
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesDto getUserPreferences(Long userId) {
        Map<String, String> prefs = loadUserPreferences(userId);

        UserPreferencesDto dto = new UserPreferencesDto();
        dto.setLocale(prefs.get("locale"));
        dto.setTimezone(prefs.get("timezone"));
        dto.setDateFormat(prefs.get("dateFormat"));
        dto.setTimeFormat(prefs.get("timeFormat"));
        dto.setTheme(prefs.get("theme"));
        dto.setNotificationPreferences(prefs.get("notificationPreferences"));
        return dto;
    }

    @Override
    @Transactional
    public void updateUserPreferences(Long userId, UserPreferencesDto dto) {
        BizUserProfile profile = bizUserProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found: " + userId));

        Map<String, String> prefs = parsePreferences(profile.getPreferences());

        // PATCH semantics
        if (dto.getLocale() != null) prefs.put("locale", dto.getLocale());
        if (dto.getTimezone() != null) prefs.put("timezone", dto.getTimezone());
        if (dto.getDateFormat() != null) prefs.put("dateFormat", dto.getDateFormat());
        if (dto.getTimeFormat() != null) prefs.put("timeFormat", dto.getTimeFormat());
        if (dto.getTheme() != null) prefs.put("theme", dto.getTheme());
        if (dto.getNotificationPreferences() != null) prefs.put("notificationPreferences", dto.getNotificationPreferences());

        profile.setPreferences(serializePreferences(prefs));
        bizUserProfileRepository.save(profile);
        log.info("Updated user preferences for userId={}", userId);
    }

    // ========================================================================
    // Branding
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public TenantBrandingDto getBranding(Long tenantId) {
        Tenant tenant = findTenant(tenantId);

        TenantBrandingDto dto = new TenantBrandingDto();
        dto.setLogoUrl(tenant.getLogoUrl());
        dto.setFaviconUrl(tenant.getFaviconUrl());
        dto.setPrimaryColor(tenant.getPrimaryColor());
        dto.setSecondaryColor(tenant.getSecondaryColor());
        dto.setLoginPageTitle(tenant.getLoginPageTitle());
        dto.setLoginPageSubtitle(tenant.getLoginPageSubtitle());
        dto.setLoginPageBgUrl(tenant.getLoginPageBgUrl());
        return dto;
    }

    @Override
    @Transactional
    public void updateBranding(Long tenantId, TenantBrandingDto dto) {
        Tenant tenant = findTenant(tenantId);

        if (dto.getLogoUrl() != null) tenant.setLogoUrl(dto.getLogoUrl());
        if (dto.getFaviconUrl() != null) tenant.setFaviconUrl(dto.getFaviconUrl());
        if (dto.getPrimaryColor() != null) tenant.setPrimaryColor(dto.getPrimaryColor());
        if (dto.getSecondaryColor() != null) tenant.setSecondaryColor(dto.getSecondaryColor());
        if (dto.getLoginPageTitle() != null) tenant.setLoginPageTitle(dto.getLoginPageTitle());
        if (dto.getLoginPageSubtitle() != null) tenant.setLoginPageSubtitle(dto.getLoginPageSubtitle());
        if (dto.getLoginPageBgUrl() != null) tenant.setLoginPageBgUrl(dto.getLoginPageBgUrl());

        tenantRepository.save(tenant);
        log.info("Updated branding for tenantId={}", tenantId);
    }

    @Override
    @Transactional
    public String uploadLogo(Long tenantId, MultipartFile file) {
        Tenant tenant = findTenant(tenantId);

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }

        // Generate a unique filename to prevent path traversal and collisions
        String filename = tenantId + "-" + UUID.randomUUID() + extension;

        try {
            Path uploadDir = Paths.get(brandingUploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            Path targetPath = uploadDir.resolve(filename).normalize();
            // Ensure the resolved path is still within the upload directory
            if (!targetPath.startsWith(uploadDir)) {
                throw new SecurityException("Invalid file path detected");
            }

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String logoUrl = brandingBaseUrl + "/" + filename;
            tenant.setLogoUrl(logoUrl);
            tenantRepository.save(tenant);

            log.info("Uploaded logo for tenantId={}: {}", tenantId, logoUrl);
            return logoUrl;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store logo file", e);
        }
    }

    // ========================================================================
    // Namespace Config
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<TenantConfig> getNamespaceConfigs(Long tenantId, String namespace) {
        return tenantConfigRepository.findByTenantIdAndConfigNamespace(tenantId, namespace);
    }

    @Override
    @Transactional
    public void putConfig(Long tenantId, String namespace, String key, TenantConfigDto dto) {
        Optional<TenantConfig> existing = tenantConfigRepository
                .findByTenantIdAndConfigNamespaceAndConfigKey(tenantId, namespace, key);

        TenantConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            if (config.isReadonly()) {
                throw new IllegalStateException("Cannot modify read-only config: " + namespace + "." + key);
            }
            config.setConfigValue(dto.getValue());
            config.setConfigType(dto.getType());
            if (dto.getDescription() != null) config.setDescription(dto.getDescription());
            if (dto.getSensitive() != null) config.setSensitive(dto.getSensitive());
        } else {
            config = new TenantConfig(tenantId, namespace, key, dto.getValue());
            config.setId(idGenerator.nextId());
            config.setConfigType(dto.getType());
            config.setDescription(dto.getDescription());
            config.setSensitive(dto.getSensitive() != null && dto.getSensitive());
        }

        tenantConfigRepository.save(config);
        log.info("Put config [{}.{}] for tenantId={}", namespace, key, tenantId);
    }

    @Override
    @Transactional
    public void deleteConfig(Long tenantId, String namespace, String key) {
        Optional<TenantConfig> existing = tenantConfigRepository
                .findByTenantIdAndConfigNamespaceAndConfigKey(tenantId, namespace, key);

        if (existing.isPresent()) {
            if (existing.get().isReadonly()) {
                throw new IllegalStateException("Cannot delete read-only config: " + namespace + "." + key);
            }
            tenantConfigRepository.delete(existing.get());
            log.info("Deleted config [{}.{}] for tenantId={}", namespace, key, tenantId);
        }
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }

    private Map<String, String> loadUserPreferences(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        return bizUserProfileRepository.findById(userId)
                .map(p -> parsePreferences(p.getPreferences()))
                .orElse(Map.of());
    }

    private Map<String, String> parsePreferences(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse user preferences JSON, returning empty: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String serializePreferences(Map<String, String> prefs) {
        try {
            return objectMapper.writeValueAsString(prefs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize preferences", e);
        }
    }

    /**
     * Three-layer merge for a single field.
     *
     * <p>Sets both the value and source on the EffectiveConfigDto.</p>
     *
     * @param dto            target DTO
     * @param field          field name
     * @param userValue      user preference (highest priority)
     * @param tenantValue    tenant setting
     * @param platformValue  platform default (lowest priority)
     */
    private void resolveField(EffectiveConfigDto dto, String field,
                              String userValue, String tenantValue, String platformValue) {
        String value;
        String source;

        if (userValue != null && !userValue.isBlank()) {
            value = userValue;
            source = "user";
        } else if (tenantValue != null && !tenantValue.isBlank()) {
            value = tenantValue;
            source = "tenant";
        } else {
            value = platformValue;
            source = "platform";
        }

        switch (field) {
            case "locale" -> { dto.setLocale(value); dto.setLocaleSource(source); }
            case "timezone" -> { dto.setTimezone(value); dto.setTimezoneSource(source); }
            case "dateFormat" -> { dto.setDateFormat(value); dto.setDateFormatSource(source); }
            case "timeFormat" -> { dto.setTimeFormat(value); dto.setTimeFormatSource(source); }
            case "currency" -> { dto.setCurrency(value); dto.setCurrencySource(source); }
            case "theme" -> { dto.setTheme(value); dto.setThemeSource(source); }
            default -> log.warn("Unknown field in resolveField: {}", field);
        }
    }
}
