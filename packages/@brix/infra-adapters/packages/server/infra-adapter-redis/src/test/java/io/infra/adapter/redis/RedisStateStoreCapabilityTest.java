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
package io.infra.adapter.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link RedisStateStoreCapability} 单元测试
 *
 * <p>使用 Mockito 模拟 RedisTemplate，验证状态存储的
 * 序列化/反序列化、TTL 处理、异常传播行为。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisStateStoreCapability 测试")
class RedisStateStoreCapabilityTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisStateStoreCapability stateStore;

    private static final String PREFIX = "shinwa:state:";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stateStore = new RedisStateStoreCapability(redisTemplate, objectMapper);
    }

    // ==================== get ====================

    @Nested
    @DisplayName("get 操作")
    class GetTests {

        @Test
        @DisplayName("键存在时应返回反序列化后的值")
        void get_shouldReturnValue_whenKeyExists() {
            when(valueOperations.get(PREFIX + "user:1")).thenReturn("{\"name\":\"Alice\",\"age\":30}");

            Optional<TestUser> result = stateStore.get("user:1", TestUser.class);

            assertThat(result).isPresent();
            assertThat(result.get().name).isEqualTo("Alice");
            assertThat(result.get().age).isEqualTo(30);
        }

        @Test
        @DisplayName("键不存在时应返回 empty")
        void get_shouldReturnEmpty_whenKeyMissing() {
            when(valueOperations.get(PREFIX + "missing")).thenReturn(null);

            Optional<TestUser> result = stateStore.get("missing", TestUser.class);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("反序列化失败时应抛出 StateStoreException")
        void get_shouldThrow_onDeserializationFailure() {
            when(valueOperations.get(PREFIX + "bad")).thenReturn("not-json");

            assertThatThrownBy(() -> stateStore.get("bad", TestUser.class))
                .isInstanceOf(StateStoreException.class);
        }

        @Test
        @DisplayName("key 为 null 时应抛出 NullPointerException")
        void get_shouldRejectNullKey() {
            assertThatThrownBy(() -> stateStore.get(null, TestUser.class))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== put ====================

    @Nested
    @DisplayName("put 操作")
    class PutTests {

        @Test
        @DisplayName("应序列化并存储值")
        void put_shouldSerializeAndStore() {
            TestUser user = new TestUser("Bob", 25);

            stateStore.put("user:2", user);

            verify(valueOperations).set(eq(PREFIX + "user:2"), anyString());
        }

        @Test
        @DisplayName("带 TTL 时应设置过期时间")
        void put_withTtl_shouldSetExpiry() {
            TestUser user = new TestUser("Charlie", 35);

            stateStore.put("user:3", user, Duration.ofMinutes(5));

            verify(valueOperations).set(eq(PREFIX + "user:3"), anyString(), eq(300000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("负 TTL 应抛出 IllegalArgumentException")
        void put_shouldRejectNegativeTtl() {
            assertThatThrownBy(() -> stateStore.put("key", "value", Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("key 为 null 时应抛出 NullPointerException")
        void put_shouldRejectNullKey() {
            assertThatThrownBy(() -> stateStore.put(null, "value"))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== remove ====================

    @Test
    @DisplayName("remove - 应删除指定键")
    void remove_shouldDeleteKey() {
        stateStore.remove("user:1");

        verify(redisTemplate).delete(PREFIX + "user:1");
    }

    // ==================== exists ====================

    @Test
    @DisplayName("exists - 键存在时应返回 true")
    void exists_shouldReturnTrue_whenKeyExists() {
        when(redisTemplate.hasKey(PREFIX + "user:1")).thenReturn(true);

        assertThat(stateStore.exists("user:1")).isTrue();
    }

    @Test
    @DisplayName("exists - 键不存在时应返回 false")
    void exists_shouldReturnFalse_whenKeyMissing() {
        when(redisTemplate.hasKey(PREFIX + "missing")).thenReturn(false);

        assertThat(stateStore.exists("missing")).isFalse();
    }

    // ==================== 构造函数 ====================

    @Test
    @DisplayName("自定义前缀应正确应用")
    void constructor_shouldUseCustomPrefix() {
        RedisStateStoreCapability custom = new RedisStateStoreCapability(
            redisTemplate, objectMapper, "custom:");
        when(valueOperations.get("custom:key")).thenReturn(null);

        custom.get("key", String.class);

        verify(valueOperations).get("custom:key");
    }

    @Test
    @DisplayName("null 前缀应回退到默认前缀")
    void constructor_shouldFallbackToDefaultPrefix_whenNull() {
        RedisStateStoreCapability custom = new RedisStateStoreCapability(
            redisTemplate, objectMapper, null);
        when(valueOperations.get(PREFIX + "key")).thenReturn(null);

        custom.get("key", String.class);

        verify(valueOperations).get(PREFIX + "key");
    }

    // ==================== 测试辅助类 ====================

    static class TestUser {
        public String name;
        public int age;

        public TestUser() {}

        public TestUser(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}
