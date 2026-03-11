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
package io.infra.adapter.fallback;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.ResilienceCapability;

/**
 * Auto-configuration tests for {@link FallbackCapabilitiesAutoConfiguration}
 *
 * <p>Validates the {@code @ConditionalOnMissingBean} conditional registration logic:
 * registers fallback implementations when no competing beans exist, skips when custom beans are present.</p>
 *
 * <h3>AuthContextCapability Special Handling</h3>
 * <p>The fallback AuthContextCapability requires explicit opt-in via {@code brix.fallback.auth.enabled=true}
 * as a security measure. Test cases that expect AuthContextCapability must set this property.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackCapabilitiesAutoConfiguration Tests")
class FallbackCapabilitiesAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FallbackCapabilitiesAutoConfiguration.class));

    @Test
    @DisplayName("Should register all 5 fallback capabilities when no other beans are provided and auth is enabled")
    void shouldRegisterAllFallbackBeans_whenNoneProvided() {
        contextRunner
            .withPropertyValues("brix.fallback.auth.enabled=true")
            .run(context -> {
            assertThat(context).hasSingleBean(AuthContextCapability.class);
            assertThat(context).hasSingleBean(ObservabilityCapability.class);
            assertThat(context).hasSingleBean(ConfigStoreCapability.class);
            assertThat(context).hasSingleBean(LifecycleCapability.class);
            assertThat(context).hasSingleBean(ResilienceCapability.class);

            assertThat(context.getBean(AuthContextCapability.class))
                .isInstanceOf(FallbackAuthContextCapability.class);
            assertThat(context.getBean(ObservabilityCapability.class))
                .isInstanceOf(FallbackObservabilityCapability.class);
            assertThat(context.getBean(ConfigStoreCapability.class))
                .isInstanceOf(FallbackConfigStoreCapability.class);
            assertThat(context.getBean(LifecycleCapability.class))
                .isInstanceOf(FallbackLifecycleCapability.class);
            assertThat(context.getBean(ResilienceCapability.class))
                .isInstanceOf(FallbackResilienceCapability.class);
        });
    }

    @Test
    @DisplayName("Should skip fallback AuthContextCapability when custom is provided")
    void shouldSkipFallbackAuth_whenCustomProvided() {
        contextRunner
            .withBean(AuthContextCapability.class, CustomAuthContext::new)
            .run(context -> {
                assertThat(context).hasSingleBean(AuthContextCapability.class);
                assertThat(context.getBean(AuthContextCapability.class))
                    .isInstanceOf(CustomAuthContext.class);
                // Other fallback beans should still be registered
                assertThat(context).hasSingleBean(ObservabilityCapability.class);
                assertThat(context.getBean(ObservabilityCapability.class))
                    .isInstanceOf(FallbackObservabilityCapability.class);
            });
    }

    @Test
    @DisplayName("Should skip fallback ResilienceCapability when custom is provided")
    void shouldSkipFallbackResilience_whenCustomProvided() {
        contextRunner
            .withBean(ResilienceCapability.class, CustomResilience::new)
            .run(context -> {
                assertThat(context).hasSingleBean(ResilienceCapability.class);
                assertThat(context.getBean(ResilienceCapability.class))
                    .isInstanceOf(CustomResilience.class);
                // Other fallback beans should still be registered
                assertThat(context).hasSingleBean(ConfigStoreCapability.class);
                assertThat(context.getBean(ConfigStoreCapability.class))
                    .isInstanceOf(FallbackConfigStoreCapability.class);
            });
    }

    // ==================== Custom Test Doubles ====================

    /**
     * Custom authentication context (used to test @ConditionalOnMissingBean skip logic)
     */
    static class CustomAuthContext implements AuthContextCapability {
        @Override public java.security.Principal getCurrentPrincipal() { return () -> "custom"; }
        @Override public boolean hasPermission(String p) { return false; }
        @Override public boolean hasRole(String r) { return false; }
        @Override public java.util.Set<io.runtime.sdk.capability.DataScope> getAuthorizedScopes() {
            return java.util.Set.of();
        }
    }

    /**
     * Custom resilience capability (used to test @ConditionalOnMissingBean skip logic)
     */
    static class CustomResilience implements ResilienceCapability {
        @Override
        public <T> T executeWithCircuitBreaker(String name, java.util.function.Supplier<T> operation) {
            return operation.get();
        }
        @Override
        public <T> T executeWithFallback(String name, java.util.function.Supplier<T> primary,
                java.util.function.Supplier<T> fallback) {
            return primary.get();
        }
        @Override
        public io.runtime.sdk.capability.CircuitBreakerState getCircuitBreakerState(String name) {
            return io.runtime.sdk.capability.CircuitBreakerState.CLOSED;
        }
        @Override public boolean isRateLimited(String key) { return false; }
        @Override public boolean tryAcquire(String key, int permits) { return true; }
    }
}
