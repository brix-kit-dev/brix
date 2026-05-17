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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO for the effective (merged) configuration response.
 *
 * <p>Each configuration field is annotated with its source layer:
 * {@code "user"}, {@code "tenant"}, or {@code "platform"}.
 * This allows the frontend to display origin indicators in the settings UI.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons DTO</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public class EffectiveConfigDto {

    private String locale;
    private String localeSource;

    private String timezone;
    private String timezoneSource;

    private String dateFormat;
    private String dateFormatSource;

    private String timeFormat;
    private String timeFormatSource;

    private String currency;
    private String currencySource;

    private String theme;
    private String themeSource;

    /**
     * Converts this DTO to a map with source annotations.
     *
     * @return map of {@code { "locale": "zh-CN", "locale_source": "tenant", ... }}
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("locale", locale);
        map.put("locale_source", localeSource);
        map.put("timezone", timezone);
        map.put("timezone_source", timezoneSource);
        map.put("dateFormat", dateFormat);
        map.put("dateFormat_source", dateFormatSource);
        map.put("timeFormat", timeFormat);
        map.put("timeFormat_source", timeFormatSource);
        map.put("currency", currency);
        map.put("currency_source", currencySource);
        map.put("theme", theme);
        map.put("theme_source", themeSource);
        return map;
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getLocaleSource() {
        return localeSource;
    }

    public void setLocaleSource(String localeSource) {
        this.localeSource = localeSource;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getTimezoneSource() {
        return timezoneSource;
    }

    public void setTimezoneSource(String timezoneSource) {
        this.timezoneSource = timezoneSource;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getDateFormatSource() {
        return dateFormatSource;
    }

    public void setDateFormatSource(String dateFormatSource) {
        this.dateFormatSource = dateFormatSource;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public String getTimeFormatSource() {
        return timeFormatSource;
    }

    public void setTimeFormatSource(String timeFormatSource) {
        this.timeFormatSource = timeFormatSource;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCurrencySource() {
        return currencySource;
    }

    public void setCurrencySource(String currencySource) {
        this.currencySource = currencySource;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getThemeSource() {
        return themeSource;
    }

    public void setThemeSource(String themeSource) {
        this.themeSource = themeSource;
    }
}
