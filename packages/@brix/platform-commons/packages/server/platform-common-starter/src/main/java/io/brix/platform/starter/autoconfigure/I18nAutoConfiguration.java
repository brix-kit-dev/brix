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

import io.brix.platform.starter.registration.PluginManifest;
import io.brix.platform.starter.registration.PluginManifestScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration for Brix platform internationalization (i18n) support.
 *
 * <h2>Overview</h2>
 * <p>Assembles a composite {@link ResourceBundleMessageSource} by combining:</p>
 * <ol>
 *   <li><strong>Explicit base names</strong> from {@link I18nProperties#getBaseNames()}</li>
 *   <li><strong>Auto-discovered plugin bundles</strong> following the convention
 *       {@code messages/{plugin-name}/messages}</li>
 *   <li><strong>Additional base names</strong> from {@link I18nProperties#getAdditionalBaseNames()}</li>
 * </ol>
 *
 * <h2>Plugin Convention</h2>
 * <p>Each plugin that provides a {@code META-INF/plugin-manifest.json} with a {@code "name"}
 * field is automatically registered. For example, a plugin with name {@code "case"} should
 * place its message bundles at:</p>
 * <pre>{@code
 * src/main/resources/
 *   messages/case/messages.properties            # default (English)
 *   messages/case/messages_zh_CN.properties      # Chinese (Simplified)
 *   messages/case/messages_ja_JP.properties       # Japanese
 * }</pre>
 *
 * <h2>Resolution Order</h2>
 * <p>Spring's {@link ResourceBundleMessageSource} resolves keys from the first bundle
 * that contains a match. Base names added first have higher priority, so the order is:</p>
 * <ol>
 *   <li>{@code brix.i18n.base-names} — platform-level overrides (highest priority)</li>
 *   <li>{@code messages/{plugin-name}/messages} — auto-discovered plugin bundles</li>
 *   <li>{@code brix.i18n.additional-base-names} — project-specific fallbacks</li>
 * </ol>
 *
 * <h2>Locale Resolution</h2>
 * <p>Registers an {@link AcceptHeaderLocaleResolver} that reads the {@code Accept-Language}
 * HTTP header. The default locale falls back to {@link I18nProperties#getDefaultLocale()}.</p>
 *
 * <h2>Activation</h2>
 * <ul>
 *   <li>Enabled by default. Set {@code brix.i18n.enabled=false} to disable.</li>
 *   <li>Requires a {@link PluginManifestScanner} bean (provided by
 *       {@link ServiceRegistrationAutoConfiguration}).</li>
 *   <li>Backs off if a custom {@link MessageSource} bean is already defined.</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @version 3.1.0
 * @since 3.1.0
 * @see I18nProperties
 * @see PluginManifestScanner
 * @see ResourceBundleMessageSource
 */
@AutoConfiguration(after = ServiceRegistrationAutoConfiguration.class)
@ConditionalOnBean(PluginManifestScanner.class)
@ConditionalOnProperty(
        prefix = "brix.i18n",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(I18nProperties.class)
public class I18nAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(I18nAutoConfiguration.class);

    /**
     * Plugin message bundle base-name prefix. Each plugin's bundle is resolved as
     * {@code messages/{pluginName}/messages}.
     */
    private static final String PLUGIN_MESSAGES_PREFIX = "messages/";
    private static final String PLUGIN_MESSAGES_SUFFIX = "/messages";

    /**
     * Creates a composite {@link MessageSource} that aggregates platform-level,
     * auto-discovered plugin, and project-specific message bundles.
     *
     * <p>The scanner discovers all plugins that publish
     * {@code META-INF/plugin-manifest.json} on the classpath. For each plugin
     * whose manifest contains a non-blank {@code name}, a base name of
     * {@code messages/{name}/messages} is registered.</p>
     *
     * @param scanner    the plugin manifest scanner for auto-discovery
     * @param properties i18n configuration properties
     * @return a fully-configured {@link ResourceBundleMessageSource}
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageSource messageSource(PluginManifestScanner scanner,
                                       I18nProperties properties) {

        List<String> allBaseNames = new ArrayList<>();

        // 1. Explicit base names (highest priority)
        allBaseNames.addAll(properties.getBaseNames());

        // 2. Auto-discovered plugin bundles
        List<PluginManifest> manifests = scanner.scanManifests();
        for (PluginManifest manifest : manifests) {
            String pluginName = manifest.getName();
            if (pluginName != null && !pluginName.isBlank()) {
                String baseName = PLUGIN_MESSAGES_PREFIX + pluginName + PLUGIN_MESSAGES_SUFFIX;
                allBaseNames.add(baseName);
                log.debug("[I18nAutoConfiguration] Registered i18n base name for plugin '{}': {}",
                        pluginName, baseName);
            }
        }

        // 3. Additional base names (lowest priority / project-specific fallbacks)
        allBaseNames.addAll(properties.getAdditionalBaseNames());

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(allBaseNames.toArray(String[]::new));
        messageSource.setDefaultEncoding(properties.getEncoding().name());
        messageSource.setUseCodeAsDefaultMessage(properties.isUseCodeAsDefaultMessage());
        messageSource.setCacheSeconds(properties.getCacheSeconds());
        messageSource.setDefaultLocale(properties.getDefaultLocale());
        // Fall through to parent if key is not found in any bundle
        messageSource.setFallbackToSystemLocale(false);

        log.info("[I18nAutoConfiguration] Registered {} i18n base names: {}",
                allBaseNames.size(), allBaseNames);

        return messageSource;
    }

    /**
     * Registers a {@link LocaleResolver} that reads the {@code Accept-Language}
     * HTTP header to determine the client locale. Falls back to
     * {@link I18nProperties#getDefaultLocale()} when the header is absent.
     *
     * <p>Backs off if a custom {@link LocaleResolver} is already defined
     * (e.g., session-based or cookie-based resolver).</p>
     *
     * @param properties i18n configuration properties
     * @return an {@link AcceptHeaderLocaleResolver} configured with the default locale
     */
    @Bean
    @ConditionalOnMissingBean
    public LocaleResolver localeResolver(I18nProperties properties) {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(properties.getDefaultLocale());
        log.info("[I18nAutoConfiguration] Locale resolver configured with default locale: {}",
                properties.getDefaultLocale());
        return resolver;
    }
}
