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
 * {@link FallbackConfigStoreCapability} 单元测试
 *
 * <p>验证基于系统属性和环境变量的配置读取及类型转换逻辑。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("FallbackConfigStoreCapability 测试")
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

    // ==================== 系统属性查找 ====================

    @Test
    @DisplayName("get - 系统属性存在时应返回其值")
    void get_shouldReturnSystemProperty_whenExists() {
        System.setProperty(TEST_KEY, "hello");

        Optional<String> result = configStore.get(TEST_KEY, String.class);

        assertThat(result).contains("hello");
    }

    @Test
    @DisplayName("get - 系统属性和环境变量均不存在时应返回 empty")
    void get_shouldReturnEmpty_whenNeitherExists() {
        String nonExistentKey = "brix.test.nonexistent." + System.nanoTime();

        Optional<String> result = configStore.get(nonExistentKey, String.class);

        assertThat(result).isEmpty();
    }

    // ==================== 类型转换 ====================

    @Nested
    @DisplayName("类型转换测试")
    class TypeConversionTests {

        @Test
        @DisplayName("get - 应正确转换为 Integer")
        void get_shouldConvertToInteger() {
            System.setProperty(TEST_KEY, "42");

            Optional<Integer> result = configStore.get(TEST_KEY, Integer.class);

            assertThat(result).contains(42);
        }

        @Test
        @DisplayName("get - 应正确转换为 Long")
        void get_shouldConvertToLong() {
            System.setProperty(TEST_KEY, "9876543210");

            Optional<Long> result = configStore.get(TEST_KEY, Long.class);

            assertThat(result).contains(9876543210L);
        }

        @Test
        @DisplayName("get - 应正确转换为 Boolean")
        void get_shouldConvertToBoolean() {
            System.setProperty(TEST_KEY, "true");

            Optional<Boolean> result = configStore.get(TEST_KEY, Boolean.class);

            assertThat(result).contains(true);
        }

        @Test
        @DisplayName("get - 应正确转换为 Double")
        void get_shouldConvertToDouble() {
            System.setProperty(TEST_KEY, "3.14");

            Optional<Double> result = configStore.get(TEST_KEY, Double.class);

            assertThat(result).contains(3.14);
        }

        @Test
        @DisplayName("get - 数值格式错误时应返回 empty")
        void get_shouldReturnEmpty_onNumberFormatException() {
            System.setProperty(TEST_KEY, "not-a-number");

            Optional<Integer> result = configStore.get(TEST_KEY, Integer.class);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("get - int.class 和 Integer.class 应等效")
        void get_shouldHandlePrimitiveTypes() {
            System.setProperty(TEST_KEY, "100");

            Optional<Integer> result = configStore.get(TEST_KEY, int.class);

            assertThat(result).contains(100);
        }
    }
}
