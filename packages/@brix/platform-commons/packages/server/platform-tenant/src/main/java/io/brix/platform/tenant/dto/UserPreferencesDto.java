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
import jakarta.validation.constraints.Size;

/**
 * DTO for partial updates of user preferences within a tenant.
 *
 * <p>Supports PATCH semantics — only non-null fields are applied to
 * the user's {@code biz_user_profile.preferences} JSONB column.
 * Used by {@code PATCH /api/v1/user/preferences}.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons DTO</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public class UserPreferencesDto {

    @Size(max = 20)
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Locale must be in format like 'zh-CN' or 'en'")
    private String locale;

    @Size(max = 50)
    private String timezone;

    @Size(max = 20)
    private String dateFormat;

    @Pattern(regexp = "^(12h|24h)$", message = "Time format must be '12h' or '24h'")
    private String timeFormat;

    @Pattern(regexp = "^(light|dark|system)$", message = "Theme must be light, dark, or system")
    private String theme;

    private String notificationPreferences;

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getNotificationPreferences() {
        return notificationPreferences;
    }

    public void setNotificationPreferences(String notificationPreferences) {
        this.notificationPreferences = notificationPreferences;
    }
}
