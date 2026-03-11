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
 * Unit tests for {@link FallbackObservabilityCapability}
 *
 * <p>Validates the SLF4J-based observability fallback implementation:
 * log delegation, metric recording without exceptions, and empty SpanContext.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackObservabilityCapability Tests")
class FallbackObservabilityCapabilityTest {

    private FallbackObservabilityCapability observability;

    @BeforeEach
    void setUp() {
        observability = new FallbackObservabilityCapability();
    }

    // ==================== log ====================

    @Test
    @DisplayName("log - should not throw for any log level")
    void log_shouldNotThrow_forEachLevel() {
        for (LogLevel level : LogLevel.values()) {
            assertThatNoException().isThrownBy(() ->
                observability.log(level, "test message: {}", "arg1")
            );
        }
    }

    @Test
    @DisplayName("log - should not throw with null arguments")
    void log_shouldHandleNullArgs() {
        assertThatNoException().isThrownBy(() ->
            observability.log(LogLevel.INFO, "test {}", (Object[]) null)
        );
    }

    // ==================== recordMetric ====================

    @Test
    @DisplayName("recordMetric - should record metrics without throwing")
    void recordMetric_shouldNotThrow() {
        assertThatNoException().isThrownBy(() ->
            observability.recordMetric("test.counter", 1.0, Map.of("env", "test"))
        );
    }

    @Test
    @DisplayName("recordMetric - should handle empty tags")
    void recordMetric_shouldHandleEmptyTags() {
        assertThatNoException().isThrownBy(() ->
            observability.recordMetric("test.gauge", 42.5, Map.of())
        );
    }

    // ==================== currentSpan ====================

    @Test
    @DisplayName("currentSpan - should return empty SpanContext")
    void currentSpan_shouldReturnEmpty() {
        SpanContext span = observability.currentSpan();

        assertThat(span).isNotNull();
        assertThat(span).isEqualTo(SpanContext.empty());
    }

    // ==================== addSpanAttribute ====================

    @Test
    @DisplayName("addSpanAttribute - should handle without throwing")
    void addSpanAttribute_shouldNotThrow() {
        assertThatNoException().isThrownBy(() ->
            observability.addSpanAttribute("user.id", "12345")
        );
    }
}
