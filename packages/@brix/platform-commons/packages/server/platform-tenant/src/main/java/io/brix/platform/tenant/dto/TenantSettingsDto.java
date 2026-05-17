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
package io.brix.platform.tenant.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO for partial updates of tenant settings.
 *
 * <p>Supports PATCH semantics — only non-null fields are applied.
 * Used by {@code PATCH /api/v1/tenant/settings}.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons DTO</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public class TenantSettingsDto {

    @Size(max = 20)
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Locale must be in format like 'zh-CN' or 'en'")
    private String defaultLocale;

    @Size(max = 50)
    private String defaultTimezone;

    @Size(max = 20)
    private String defaultDateFormat;

    @Pattern(regexp = "^(12h|24h)$", message = "Time format must be '12h' or '24h'")
    private String defaultTimeFormat;

    @Size(max = 10)
    private String defaultCurrency;

    @Pattern(regexp = "^(LIGHT|DARK|SYSTEM)$", message = "Theme must be LIGHT, DARK, or SYSTEM")
    private String defaultTheme;

    @Positive
    private Integer sessionTimeoutMinutes;

    @Pattern(regexp = "^(DISABLED|OPTIONAL|REQUIRED)$", message = "MFA policy must be DISABLED, OPTIONAL, or REQUIRED")
    private String mfaPolicy;

    private String allowedLoginMethods;

    private String passwordPolicy;

    private String notificationChannels;

    private String businessHours;

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public void setDefaultTimezone(String defaultTimezone) {
        this.defaultTimezone = defaultTimezone;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    public void setDefaultDateFormat(String defaultDateFormat) {
        this.defaultDateFormat = defaultDateFormat;
    }

    public String getDefaultTimeFormat() {
        return defaultTimeFormat;
    }

    public void setDefaultTimeFormat(String defaultTimeFormat) {
        this.defaultTimeFormat = defaultTimeFormat;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public String getDefaultTheme() {
        return defaultTheme;
    }

    public void setDefaultTheme(String defaultTheme) {
        this.defaultTheme = defaultTheme;
    }

    public Integer getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(Integer sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    public String getMfaPolicy() {
        return mfaPolicy;
    }

    public void setMfaPolicy(String mfaPolicy) {
        this.mfaPolicy = mfaPolicy;
    }

    public String getAllowedLoginMethods() {
        return allowedLoginMethods;
    }

    public void setAllowedLoginMethods(String allowedLoginMethods) {
        this.allowedLoginMethods = allowedLoginMethods;
    }

    public String getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(String passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public String getNotificationChannels() {
        return notificationChannels;
    }

    public void setNotificationChannels(String notificationChannels) {
        this.notificationChannels = notificationChannels;
    }

    public String getBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(String businessHours) {
        this.businessHours = businessHours;
    }
}
