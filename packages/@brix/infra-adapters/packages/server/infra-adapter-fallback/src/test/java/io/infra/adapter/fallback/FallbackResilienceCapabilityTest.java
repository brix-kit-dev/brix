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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.CircuitBreakerState;

/**
 * {@link FallbackResilienceCapability} 单元测试
 *
 * <p>验证透传式韧性能力实现的行为：
 * 熔断器始终关闭、限流器始终放行、降级时调用 fallback 函数。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackResilienceCapability 测试")
class FallbackResilienceCapabilityTest {

    private FallbackResilienceCapability resilience;

    @BeforeEach
    void setUp() {
        resilience = new FallbackResilienceCapability();
    }

    // ==================== executeWithCircuitBreaker ====================

    @Test
    @DisplayName("executeWithCircuitBreaker - 应直接执行操作并返回结果")
    void executeWithCircuitBreaker_shouldExecuteOperation() {
        String result = resilience.executeWithCircuitBreaker("test-breaker", () -> "hello");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("executeWithCircuitBreaker - 操作抛出异常时应向上传播")
    void executeWithCircuitBreaker_shouldPropagateException() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            resilience.executeWithCircuitBreaker("test-breaker", () -> {
                throw new RuntimeException("operation failed");
            })
        );
    }

    @Test
    @DisplayName("executeWithCircuitBreaker - 应返回 null 操作的结果")
    void executeWithCircuitBreaker_shouldReturnNull_whenOperationReturnsNull() {
        String result = resilience.executeWithCircuitBreaker("test-breaker", () -> null);

        assertThat(result).isNull();
    }

    // ==================== executeWithFallback ====================

    @Test
    @DisplayName("executeWithFallback - 操作成功时应返回操作结果")
    void executeWithFallback_shouldReturnResult_onSuccess() {
        String result = resilience.executeWithFallback(
            "test-breaker",
            () -> "primary",
            () -> "fallback"
        );

        assertThat(result).isEqualTo("primary");
    }

    @Test
    @DisplayName("executeWithFallback - 操作失败时应返回降级结果")
    void executeWithFallback_shouldReturnFallback_onException() {
        String result = resilience.executeWithFallback(
            "test-breaker",
            () -> { throw new RuntimeException("fail"); },
            () -> "fallback-value"
        );

        assertThat(result).isEqualTo("fallback-value");
    }

    @Test
    @DisplayName("executeWithFallback - 不同异常类型均应触发降级")
    void executeWithFallback_shouldHandleVariousExceptions() {
        String result1 = resilience.executeWithFallback("b1",
            () -> { throw new IllegalStateException("state"); }, () -> "fb1");
        String result2 = resilience.executeWithFallback("b2",
            () -> { throw new NullPointerException("np"); }, () -> "fb2");

        assertThat(result1).isEqualTo("fb1");
        assertThat(result2).isEqualTo("fb2");
    }

    // ==================== getCircuitBreakerState ====================

    @Test
    @DisplayName("getCircuitBreakerState - 应始终返回 CLOSED")
    void getCircuitBreakerState_shouldAlwaysReturnClosed() {
        assertThat(resilience.getCircuitBreakerState("any-breaker")).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(resilience.getCircuitBreakerState("another")).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(resilience.getCircuitBreakerState(null)).isEqualTo(CircuitBreakerState.CLOSED);
    }

    // ==================== isRateLimited ====================

    @Test
    @DisplayName("isRateLimited - 应始终返回 false")
    void isRateLimited_shouldAlwaysReturnFalse() {
        assertThat(resilience.isRateLimited("any-key")).isFalse();
        assertThat(resilience.isRateLimited("another-key")).isFalse();
        assertThat(resilience.isRateLimited(null)).isFalse();
    }

    // ==================== tryAcquire ====================

    @Test
    @DisplayName("tryAcquire - 应始终返回 true")
    void tryAcquire_shouldAlwaysReturnTrue() {
        assertThat(resilience.tryAcquire("any-key", 1)).isTrue();
        assertThat(resilience.tryAcquire("any-key", 100)).isTrue();
        assertThat(resilience.tryAcquire("any-key", 0)).isTrue();
    }
}
