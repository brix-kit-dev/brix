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
package io.brix.platform.starter.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuration properties for the Brix i18n / MessageSource auto-configuration.
 *
 * <p>Controls how plugin resource bundles are discovered and how the composite
 * {@link org.springframework.context.MessageSource} is assembled.</p>
 *
 * <h3>YAML Example</h3>
 * <pre>{@code
 * brix:
 *   i18n:
 *     enabled: true
 *     default-locale: en
 *     encoding: UTF-8
 *     use-code-as-default-message: true
 *     cache-seconds: 3600
 *     base-names:
 *       - messages/platform/messages    # platform-level messages
 *     additional-base-names:
 *       - messages/custom/messages      # project-specific overrides
 * }</pre>
 *
 * <h3>Plugin Convention</h3>
 * <p>Each plugin can provide its own messages by placing resource bundles at:</p>
 * <pre>{@code
 * src/main/resources/
 *   messages/{plugin-id}/messages.properties          # default (English)
 *   messages/{plugin-id}/messages_zh_CN.properties    # Chinese
 *   messages/{plugin-id}/messages_ja_JP.properties    # Japanese
 * }</pre>
 *
 * <p>Plugin bundles are auto-discovered by scanning {@code META-INF/plugin-manifest.json}
 * and registering {@code messages/{pluginId}/messages} as base names.</p>
 *
 * @author Brix Platform Team
 * @version 3.1.0
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "brix.i18n")
public class I18nProperties {

    /**
     * Whether the Brix i18n auto-configuration is enabled.
     */
    private boolean enabled = true;

    /**
     * Default locale to use when no locale is specified.
     */
    private Locale defaultLocale = Locale.ENGLISH;

    /**
     * Character encoding for message resource bundles.
     */
    private Charset encoding = StandardCharsets.UTF_8;

    /**
     * Whether to use the message code as the default message when no translation is found.
     * When {@code true}, untranslated keys return the key itself instead of throwing
     * {@code NoSuchMessageException}. This is the recommended behavior for development
     * and production to guarantee that UI never displays raw exception messages.
     */
    private boolean useCodeAsDefaultMessage = true;

    /**
     * Duration (in seconds) to cache loaded message bundles.
     * Set to {@code -1} to cache forever (recommended for production).
     * Set to {@code 0} to disable caching (useful during development for hot-reload).
     */
    private int cacheSeconds = -1;

    /**
     * Explicit base names for message resource bundles (in addition to auto-discovered
     * plugin bundles). These are resolved relative to the classpath.
     *
     * <p>Example:</p>
     * <pre>{@code
     * base-names:
     *   - messages/platform/messages
     *   - messages/shared/validation
     * }</pre>
     */
    private List<String> baseNames = new ArrayList<>();

    /**
     * Additional base names appended after auto-discovered plugin bundles.
     * Useful for project-specific overrides that should take lowest priority.
     */
    private List<String> additionalBaseNames = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    public boolean isUseCodeAsDefaultMessage() {
        return useCodeAsDefaultMessage;
    }

    public void setUseCodeAsDefaultMessage(boolean useCodeAsDefaultMessage) {
        this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    public List<String> getBaseNames() {
        return baseNames;
    }

    public void setBaseNames(List<String> baseNames) {
        this.baseNames = baseNames;
    }

    public List<String> getAdditionalBaseNames() {
        return additionalBaseNames;
    }

    public void setAdditionalBaseNames(List<String> additionalBaseNames) {
        this.additionalBaseNames = additionalBaseNames;
    }
}
