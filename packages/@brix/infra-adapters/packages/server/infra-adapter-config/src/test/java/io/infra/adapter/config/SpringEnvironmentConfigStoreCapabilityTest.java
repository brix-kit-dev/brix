/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.config;

import io.runtime.sdk.capability.ConfigNotFoundException;
import io.runtime.sdk.capability.ConfigStoreCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpringEnvironmentConfigStoreCapability} and its auto-configuration.
 *
 * <p>Uses Spring Boot's {@link ApplicationContextRunner} to verify bean registration
 * and actual property resolution behavior without starting a full application context.</p>
 *
 * @author Brix Team
 * @since 3.1.0
 */
class SpringEnvironmentConfigStoreCapabilityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigAdapterAutoConfiguration.class));

    @Nested
    @DisplayName("Auto-configuration registration")
    class AutoConfigTests {

        @Test
        @DisplayName("should register ConfigStoreCapability bean by default")
        void shouldRegisterBeanByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ConfigStoreCapability.class);
                assertThat(context).hasSingleBean(SpringEnvironmentConfigStoreCapability.class);
            });
        }

        @Test
        @DisplayName("should not register bean when disabled via property")
        void shouldNotRegisterWhenDisabled() {
            contextRunner
                .withPropertyValues("brix.infra.config.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ConfigStoreCapability.class);
                });
        }

        @Test
        @DisplayName("should not register when another ConfigStoreCapability is present")
        void shouldNotOverrideExistingBean() {
            contextRunner
                .withBean(ConfigStoreCapability.class, () -> new ConfigStoreCapability() {
                    @Override
                    public <T> java.util.Optional<T> get(String key, Class<T> type) {
                        return java.util.Optional.empty();
                    }
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(ConfigStoreCapability.class);
                    assertThat(context).doesNotHaveBean(SpringEnvironmentConfigStoreCapability.class);
                });
        }
    }

    @Nested
    @DisplayName("Property resolution via Spring Environment")
    class PropertyResolutionTests {

        @Test
        @DisplayName("should resolve string property from application properties")
        void shouldResolveStringProperty() {
            contextRunner
                .withPropertyValues("booking.name=Test Booking Service")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    assertThat(config.getString("booking.name")).contains("Test Booking Service");
                });
        }

        @Test
        @DisplayName("should resolve integer property")
        void shouldResolveIntegerProperty() {
            contextRunner
                .withPropertyValues("booking.max-days-ahead=30")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    assertThat(config.getInt("booking.max-days-ahead", 0)).isEqualTo(30);
                });
        }

        @Test
        @DisplayName("should resolve boolean property")
        void shouldResolveBooleanProperty() {
            contextRunner
                .withPropertyValues("feature.new-ui.enabled=true")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    assertThat(config.getBoolean("feature.new-ui.enabled", false)).isTrue();
                });
        }

        @Test
        @DisplayName("should resolve long property")
        void shouldResolveLongProperty() {
            contextRunner
                .withPropertyValues("identity.jwt.expire-seconds=3600")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    assertThat(config.getLong("identity.jwt.expire-seconds", 0L)).isEqualTo(3600L);
                });
        }

        @Test
        @DisplayName("should resolve double property")
        void shouldResolveDoubleProperty() {
            contextRunner
                .withPropertyValues("pricing.tax-rate=0.08")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    assertThat(config.getDouble("pricing.tax-rate", 0.0)).isEqualTo(0.08);
                });
        }

        @Test
        @DisplayName("should return default value when key not found")
        void shouldReturnDefaultWhenMissing() {
            contextRunner.run(context -> {
                ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                assertThat(config.getString("nonexistent.key", "default")).isEqualTo("default");
                assertThat(config.getInt("nonexistent.key", 42)).isEqualTo(42);
                assertThat(config.getBoolean("nonexistent.key", true)).isTrue();
            });
        }

        @Test
        @DisplayName("should return empty Optional when key not found")
        void shouldReturnEmptyOptionalWhenMissing() {
            contextRunner.run(context -> {
                ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                assertThat(config.get("nonexistent.key", String.class)).isEmpty();
            });
        }

        @Test
        @DisplayName("should check existence correctly")
        void shouldCheckExistence() {
            contextRunner
                .withPropertyValues("existing.key=value")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    assertThat(config.exists("existing.key")).isTrue();
                    assertThat(config.exists("nonexistent.key")).isFalse();
                });
        }

        @Test
        @DisplayName("should throw ConfigNotFoundException for getRequired on missing key")
        void shouldThrowOnRequiredMissing() {
            contextRunner.run(context -> {
                ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                assertThatThrownBy(() -> config.getRequired("missing.required.key", String.class))
                    .isInstanceOf(ConfigNotFoundException.class)
                    .hasMessageContaining("missing.required.key");
            });
        }

        @Test
        @DisplayName("should resolve Spring relaxed binding (camelCase, kebab-case)")
        void shouldResolveRelaxedBinding() {
            contextRunner
                .withPropertyValues("booking.maxDaysAhead=45")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    // Spring relaxed binding maps camelCase to kebab-case
                    assertThat(config.getInt("booking.max-days-ahead", 0)).isEqualTo(45);
                });
        }

        @Test
        @DisplayName("should handle type conversion failure gracefully")
        void shouldHandleConversionFailure() {
            contextRunner
                .withPropertyValues("bad.number=not-a-number")
                .run(context -> {
                    ConfigStoreCapability config = context.getBean(ConfigStoreCapability.class);
                    // Should return empty Optional instead of throwing
                    assertThat(config.get("bad.number", Integer.class)).isEmpty();
                });
        }
    }
}
