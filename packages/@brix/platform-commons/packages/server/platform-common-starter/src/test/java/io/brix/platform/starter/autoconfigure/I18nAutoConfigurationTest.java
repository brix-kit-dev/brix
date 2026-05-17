/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.platform.starter.autoconfigure;

import io.brix.platform.starter.registration.PluginManifest;
import io.brix.platform.starter.registration.PluginManifestScanner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link I18nAutoConfiguration}.
 *
 * <p>Validates that the auto-configuration correctly assembles a composite
 * {@link MessageSource} from explicit base names, auto-discovered plugin bundles,
 * and additional base names.</p>
 */
class I18nAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class))
            .withBean(PluginManifestScanner.class, () -> {
                PluginManifestScanner scanner = mock(PluginManifestScanner.class);
                PluginManifest casePlugin = new PluginManifest();
                casePlugin.setName("case");
                PluginManifest bookingPlugin = new PluginManifest();
                bookingPlugin.setName("booking");
                when(scanner.scanManifests()).thenReturn(List.of(casePlugin, bookingPlugin));
                return scanner;
            });

    @Test
    void autoConfigurationRegistersMessageSource() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MessageSource.class);
            assertThat(context.getBean(MessageSource.class))
                    .isInstanceOf(ResourceBundleMessageSource.class);
        });
    }

    @Test
    void autoConfigurationRegistersLocaleResolver() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LocaleResolver.class);
            assertThat(context.getBean(LocaleResolver.class))
                    .isInstanceOf(AcceptHeaderLocaleResolver.class);
        });
    }

    @Test
    void disabledWhenPropertySetToFalse() {
        contextRunner
                .withPropertyValues("brix.i18n.enabled=false")
                .run(context -> {
                    // Our LocaleResolver should not be registered when disabled
                    assertThat(context).doesNotHaveBean(LocaleResolver.class);
                    // MessageSource may still exist (Spring's default), but ours should not
                    // be a ResourceBundleMessageSource configured by I18nAutoConfiguration
                    if (context.containsBean("messageSource")) {
                        assertThat(context.getBean("messageSource"))
                                .isNotInstanceOf(ResourceBundleMessageSource.class);
                    }
                });
    }

    @Test
    void backsOffWhenCustomMessageSourceExists() {
        contextRunner
                .withBean("messageSource", MessageSource.class, ResourceBundleMessageSource::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageSource.class);
                    // The auto-configured one should not replace the user-defined bean
                });
    }

    @Test
    void useCodeAsDefaultMessageReturnsMissingKeyAsIs() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);
            // When useCodeAsDefaultMessage=true (default), unknown keys return the code
            String result = messageSource.getMessage(
                    "non.existent.key", null, Locale.ENGLISH);
            assertThat(result).isEqualTo("non.existent.key");
        });
    }

    @Test
    void noPluginsStillCreatesMessageSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class))
                .withBean(PluginManifestScanner.class, () -> {
                    PluginManifestScanner scanner = mock(PluginManifestScanner.class);
                    when(scanner.scanManifests()).thenReturn(Collections.emptyList());
                    return scanner;
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageSource.class);
                });
    }

    @Test
    void customDefaultLocaleIsApplied() {
        contextRunner
                .withPropertyValues("brix.i18n.default-locale=zh_CN")
                .run(context -> {
                    assertThat(context).hasSingleBean(LocaleResolver.class);
                    AcceptHeaderLocaleResolver resolver =
                            (AcceptHeaderLocaleResolver) context.getBean(LocaleResolver.class);
                    assertThat(resolver).extracting("defaultLocale")
                            .isEqualTo(Locale.forLanguageTag("zh-CN"));
                });
    }
}
