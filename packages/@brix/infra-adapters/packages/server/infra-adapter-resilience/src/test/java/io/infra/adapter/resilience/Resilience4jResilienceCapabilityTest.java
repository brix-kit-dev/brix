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
package io.infra.adapter.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.runtime.sdk.capability.CircuitBreakerOpenException;
import io.runtime.sdk.capability.CircuitBreakerState;
import io.runtime.sdk.capability.ResilienceCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Resilience4jResilienceCapability} and its auto-configuration.
 *
 * @author Brix Team
 * @since 3.1.0
 */
class Resilience4jResilienceCapabilityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ResilienceAdapterAutoConfiguration.class));

    @Nested
    @DisplayName("Auto-configuration registration")
    class AutoConfigTests {

        @Test
        @DisplayName("should register ResilienceCapability bean by default")
        void shouldRegisterBeanByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ResilienceCapability.class);
                assertThat(context).hasSingleBean(Resilience4jResilienceCapability.class);
                assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
                assertThat(context).hasSingleBean(RateLimiterRegistry.class);
            });
        }

        @Test
        @DisplayName("should not register bean when disabled via property")
        void shouldNotRegisterWhenDisabled() {
            contextRunner
                .withPropertyValues("brix.infra.resilience.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ResilienceCapability.class);
                });
        }

        @Test
        @DisplayName("should not override existing ResilienceCapability bean")
        void shouldNotOverrideExistingBean() {
            contextRunner
                .withBean(ResilienceCapability.class, () -> new ResilienceCapability() {
                    @Override
                    public <T> T executeWithCircuitBreaker(String name, java.util.function.Supplier<T> op) {
                        return op.get();
                    }
                    @Override
                    public <T> T executeWithFallback(String name, java.util.function.Supplier<T> op,
                            java.util.function.Supplier<T> fb) {
                        return op.get();
                    }
                    @Override
                    public CircuitBreakerState getCircuitBreakerState(String name) {
                        return CircuitBreakerState.CLOSED;
                    }
                    @Override
                    public boolean isRateLimited(String key) { return false; }
                    @Override
                    public boolean tryAcquire(String key, int permits) { return true; }
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(ResilienceCapability.class);
                    assertThat(context).doesNotHaveBean(Resilience4jResilienceCapability.class);
                });
        }
    }

    @Nested
    @DisplayName("Circuit breaker behavior")
    class CircuitBreakerTests {

        private Resilience4jResilienceCapability capability;
        private CircuitBreakerRegistry cbRegistry;

        @BeforeEach
        void setUp() {
            // Create registry with aggressive thresholds for testing
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

            cbRegistry = CircuitBreakerRegistry.of(config);
            RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();

            capability = new Resilience4jResilienceCapability(cbRegistry, rlRegistry);
        }

        @Test
        @DisplayName("should execute operation normally when circuit breaker is CLOSED")
        void shouldExecuteNormally() {
            String result = capability.executeWithCircuitBreaker("test-cb", () -> "success");
            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should return CLOSED state initially")
        void shouldReturnClosedStateInitially() {
            CircuitBreakerState state = capability.getCircuitBreakerState("test-cb");
            assertThat(state).isEqualTo(CircuitBreakerState.CLOSED);
        }

        @Test
        @DisplayName("should throw CircuitBreakerOpenException when CB is OPEN")
        void shouldThrowWhenCircuitBreakerOpen() {
            // Force circuit breaker to OPEN state by causing failures
            for (int i = 0; i < 4; i++) {
                try {
                    capability.executeWithCircuitBreaker("open-cb", () -> {
                        throw new RuntimeException("Simulated failure");
                    });
                } catch (RuntimeException ignored) {
                    // Expected
                }
            }

            // Now the circuit breaker should be OPEN
            assertThat(capability.getCircuitBreakerState("open-cb"))
                .isEqualTo(CircuitBreakerState.OPEN);

            // Further calls should throw CircuitBreakerOpenException
            assertThatThrownBy(() ->
                capability.executeWithCircuitBreaker("open-cb", () -> "should-fail"))
                .isInstanceOf(CircuitBreakerOpenException.class)
                .hasMessageContaining("open-cb");
        }

        @Test
        @DisplayName("should execute fallback when operation fails")
        void shouldExecuteFallback() {
            String result = capability.executeWithFallback(
                "fallback-cb",
                () -> { throw new RuntimeException("primary failed"); },
                () -> "fallback-value"
            );
            assertThat(result).isEqualTo("fallback-value");
        }

        @Test
        @DisplayName("should execute fallback when circuit breaker is OPEN")
        void shouldExecuteFallbackWhenOpen() {
            // Force OPEN state
            for (int i = 0; i < 4; i++) {
                capability.executeWithFallback("open-fb-cb",
                    () -> { throw new RuntimeException(); },
                    () -> "ignored");
            }

            // Verify OPEN state
            assertThat(capability.getCircuitBreakerState("open-fb-cb"))
                .isEqualTo(CircuitBreakerState.OPEN);

            // Fallback should be invoked for OPEN state
            String result = capability.executeWithFallback("open-fb-cb",
                () -> "should-not-execute",
                () -> "fallback-for-open");
            assertThat(result).isEqualTo("fallback-for-open");
        }
    }

    @Nested
    @DisplayName("Rate limiter behavior")
    class RateLimiterTests {

        private Resilience4jResilienceCapability capability;

        @BeforeEach
        void setUp() {
            CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();

            // Create a rate limiter with a very small limit for testing
            RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();

            RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(config);
            capability = new Resilience4jResilienceCapability(cbRegistry, rlRegistry);
        }

        @Test
        @DisplayName("should not be rate limited initially")
        void shouldNotBeRateLimitedInitially() {
            assertThat(capability.isRateLimited("test-rl")).isFalse();
        }

        @Test
        @DisplayName("should acquire permits successfully within limit")
        void shouldAcquirePermits() {
            assertThat(capability.tryAcquire("test-rl", 1)).isTrue();
            assertThat(capability.tryAcquire("test-rl", 1)).isTrue();
        }

        @Test
        @DisplayName("should reject when permits are exhausted")
        void shouldRejectWhenExhausted() {
            // Consume all permits (limit is 2)
            capability.tryAcquire("exhaust-rl", 1);
            capability.tryAcquire("exhaust-rl", 1);

            // Third call should fail
            assertThat(capability.tryAcquire("exhaust-rl", 1)).isFalse();
        }

        @Test
        @DisplayName("should report rate limited after permits exhausted")
        void shouldReportRateLimitedAfterExhaustion() {
            capability.tryAcquire("status-rl", 1);
            capability.tryAcquire("status-rl", 1);

            assertThat(capability.isRateLimited("status-rl")).isTrue();
        }
    }

    @Nested
    @DisplayName("Configuration via properties")
    class PropertyConfigTests {

        @Test
        @DisplayName("should apply custom circuit breaker defaults from properties")
        void shouldApplyCustomCircuitBreakerDefaults() {
            contextRunner
                .withPropertyValues(
                    "brix.infra.resilience.circuit-breaker.failure-rate-threshold=30",
                    "brix.infra.resilience.circuit-breaker.sliding-window-size=20"
                )
                .run(context -> {
                    CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
                    // Create a new instance to verify defaults
                    var cb = registry.circuitBreaker("config-test");
                    assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(30f);
                    assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(20);
                });
        }

        @Test
        @DisplayName("should apply custom rate limiter defaults from properties")
        void shouldApplyCustomRateLimiterDefaults() {
            contextRunner
                .withPropertyValues(
                    "brix.infra.resilience.rate-limiter.limit-for-period=50",
                    "brix.infra.resilience.rate-limiter.limit-refresh-period=2s"
                )
                .run(context -> {
                    RateLimiterRegistry registry = context.getBean(RateLimiterRegistry.class);
                    var rl = registry.rateLimiter("config-test");
                    assertThat(rl.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(50);
                    assertThat(rl.getRateLimiterConfig().getLimitRefreshPeriod())
                        .isEqualTo(Duration.ofSeconds(2));
                });
        }
    }
}
