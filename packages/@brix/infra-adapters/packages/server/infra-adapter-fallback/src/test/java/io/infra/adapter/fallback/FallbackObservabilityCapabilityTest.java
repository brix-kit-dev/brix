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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.LogLevel;
import io.runtime.sdk.capability.SpanContext;

/**
 * {@link FallbackObservabilityCapability} 单元测试
 *
 * <p>验证基于 SLF4J 的可观测性 Fallback 实现：
 * 日志委托、指标记录不抛异常、空 SpanContext。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackObservabilityCapability 测试")
class FallbackObservabilityCapabilityTest {

    private FallbackObservabilityCapability observability;

    @BeforeEach
    void setUp() {
        observability = new FallbackObservabilityCapability();
    }

    // ==================== log ====================

    @Test
    @DisplayName("log - 各日志级别均不应抛出异常")
    void log_shouldNotThrow_forEachLevel() {
        for (LogLevel level : LogLevel.values()) {
            assertThatNoException().isThrownBy(() ->
                observability.log(level, "test message: {}", "arg1")
            );
        }
    }

    @Test
    @DisplayName("log - null 参数不应抛出异常")
    void log_shouldHandleNullArgs() {
        assertThatNoException().isThrownBy(() ->
            observability.log(LogLevel.INFO, "test {}", (Object[]) null)
        );
    }

    // ==================== recordMetric ====================

    @Test
    @DisplayName("recordMetric - 应正常记录指标不抛异常")
    void recordMetric_shouldNotThrow() {
        assertThatNoException().isThrownBy(() ->
            observability.recordMetric("test.counter", 1.0, Map.of("env", "test"))
        );
    }

    @Test
    @DisplayName("recordMetric - 空标签应正常处理")
    void recordMetric_shouldHandleEmptyTags() {
        assertThatNoException().isThrownBy(() ->
            observability.recordMetric("test.gauge", 42.5, Map.of())
        );
    }

    // ==================== currentSpan ====================

    @Test
    @DisplayName("currentSpan - 应返回空 SpanContext")
    void currentSpan_shouldReturnEmpty() {
        SpanContext span = observability.currentSpan();

        assertThat(span).isNotNull();
        assertThat(span).isEqualTo(SpanContext.empty());
    }

    // ==================== addSpanAttribute ====================

    @Test
    @DisplayName("addSpanAttribute - 应正常处理不抛异常")
    void addSpanAttribute_shouldNotThrow() {
        assertThatNoException().isThrownBy(() ->
            observability.addSpanAttribute("user.id", "12345")
        );
    }
}
