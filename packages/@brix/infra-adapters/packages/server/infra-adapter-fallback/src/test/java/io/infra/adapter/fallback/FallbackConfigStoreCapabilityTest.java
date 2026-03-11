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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FallbackConfigStoreCapability}
 *
 * <p>Validates configuration reading from system properties and environment variables, and type conversion logic.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackConfigStoreCapability Tests")
class FallbackConfigStoreCapabilityTest {

    private FallbackConfigStoreCapability configStore;

    private static final String TEST_KEY = "brix.test.config.key";

    @BeforeEach
    void setUp() {
        configStore = new FallbackConfigStoreCapability();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(TEST_KEY);
    }

    // ==================== System Property Lookup ====================

    @Test
    @DisplayName("get - should return system property value when it exists")
    void get_shouldReturnSystemProperty_whenExists() {
        System.setProperty(TEST_KEY, "hello");

        Optional<String> result = configStore.get(TEST_KEY, String.class);

        assertThat(result).contains("hello");
    }

    @Test
    @DisplayName("get - should return empty when neither system property nor environment variable exists")
    void get_shouldReturnEmpty_whenNeitherExists() {
        String nonExistentKey = "brix.test.nonexistent." + System.nanoTime();

        Optional<String> result = configStore.get(nonExistentKey, String.class);

        assertThat(result).isEmpty();
    }

    // ==================== Type Conversion ====================

    @Nested
    @DisplayName("Type Conversion Tests")
    class TypeConversionTests {

        @Test
        @DisplayName("get - should correctly convert to Integer")
        void get_shouldConvertToInteger() {
            System.setProperty(TEST_KEY, "42");

            Optional<Integer> result = configStore.get(TEST_KEY, Integer.class);

            assertThat(result).contains(42);
        }

        @Test
        @DisplayName("get - should correctly convert to Long")
        void get_shouldConvertToLong() {
            System.setProperty(TEST_KEY, "9876543210");

            Optional<Long> result = configStore.get(TEST_KEY, Long.class);

            assertThat(result).contains(9876543210L);
        }

        @Test
        @DisplayName("get - should correctly convert to Boolean")
        void get_shouldConvertToBoolean() {
            System.setProperty(TEST_KEY, "true");

            Optional<Boolean> result = configStore.get(TEST_KEY, Boolean.class);

            assertThat(result).contains(true);
        }

        @Test
        @DisplayName("get - should correctly convert to Double")
        void get_shouldConvertToDouble() {
            System.setProperty(TEST_KEY, "3.14");

            Optional<Double> result = configStore.get(TEST_KEY, Double.class);

            assertThat(result).contains(3.14);
        }

        @Test
        @DisplayName("get - should return empty on number format error")
        void get_shouldReturnEmpty_onNumberFormatException() {
            System.setProperty(TEST_KEY, "not-a-number");

            Optional<Integer> result = configStore.get(TEST_KEY, Integer.class);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("get - int.class and Integer.class should be equivalent")
        void get_shouldHandlePrimitiveTypes() {
            System.setProperty(TEST_KEY, "100");

            Optional<Integer> result = configStore.get(TEST_KEY, int.class);

            assertThat(result).contains(100);
        }
    }
}
