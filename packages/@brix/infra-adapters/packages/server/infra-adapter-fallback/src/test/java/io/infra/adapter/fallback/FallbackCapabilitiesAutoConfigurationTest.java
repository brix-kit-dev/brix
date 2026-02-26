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
 * {@link FallbackCapabilitiesAutoConfiguration} 自动配置测试
 *
 * <p>验证 {@code @ConditionalOnMissingBean} 条件注册逻辑：
 * 无竞争 bean 时注册 fallback 实现，有自定义 bean 时跳过。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackCapabilitiesAutoConfiguration 测试")
class FallbackCapabilitiesAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FallbackCapabilitiesAutoConfiguration.class));

    @Test
    @DisplayName("无其他 bean 时应注册全部 5 个 fallback 能力")
    void shouldRegisterAllFallbackBeans_whenNoneProvided() {
        contextRunner.run(context -> {
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
    @DisplayName("有自定义 AuthContextCapability 时应跳过 fallback")
    void shouldSkipFallbackAuth_whenCustomProvided() {
        contextRunner
            .withBean(AuthContextCapability.class, CustomAuthContext::new)
            .run(context -> {
                assertThat(context).hasSingleBean(AuthContextCapability.class);
                assertThat(context.getBean(AuthContextCapability.class))
                    .isInstanceOf(CustomAuthContext.class);
                // 其他 fallback 仍应注册
                assertThat(context).hasSingleBean(ObservabilityCapability.class);
                assertThat(context.getBean(ObservabilityCapability.class))
                    .isInstanceOf(FallbackObservabilityCapability.class);
            });
    }

    @Test
    @DisplayName("有自定义 ResilienceCapability 时应跳过 fallback")
    void shouldSkipFallbackResilience_whenCustomProvided() {
        contextRunner
            .withBean(ResilienceCapability.class, CustomResilience::new)
            .run(context -> {
                assertThat(context).hasSingleBean(ResilienceCapability.class);
                assertThat(context.getBean(ResilienceCapability.class))
                    .isInstanceOf(CustomResilience.class);
                // 其他 fallback 仍应注册
                assertThat(context).hasSingleBean(ConfigStoreCapability.class);
                assertThat(context.getBean(ConfigStoreCapability.class))
                    .isInstanceOf(FallbackConfigStoreCapability.class);
            });
    }

    // ==================== 自定义测试替身 ====================

    /**
     * 自定义认证上下文（用于测试 @ConditionalOnMissingBean 跳过逻辑）
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
     * 自定义韧性能力（用于测试 @ConditionalOnMissingBean 跳过逻辑）
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
