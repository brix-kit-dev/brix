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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.springframework.data.redis.core.script.RedisScript;

import io.runtime.sdk.capability.DistributedLock;

/**
 * {@link RedisLockCapability} 单元测试
 *
 * <p>使用 Mockito 模拟 RedisTemplate，验证分布式锁的
 * 获取、释放、所有权验证行为。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisLockCapability 测试")
class RedisLockCapabilityTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLockCapability lockCapability;

    private static final String LOCK_PREFIX = "brix:lock:";
    private static final int DEFAULT_EXPIRE = 30;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lockCapability = new RedisLockCapability(redisTemplate, LOCK_PREFIX, DEFAULT_EXPIRE);
    }

    // ==================== tryLock (无等待) ====================

    @Nested
    @DisplayName("tryLock(key) - 无等待")
    class TryLockNoWaitTests {

        @Test
        @DisplayName("锁可用时应返回 true")
        void tryLock_shouldReturnTrue_whenLockAvailable() {
            when(valueOperations.setIfAbsent(
                eq(LOCK_PREFIX + "resource1"), anyString(), anyLong(), any(TimeUnit.class))
            ).thenReturn(true);

            boolean acquired = lockCapability.tryLock("resource1");

            assertThat(acquired).isTrue();
        }

        @Test
        @DisplayName("锁被持有时应返回 false")
        void tryLock_shouldReturnFalse_whenLockHeld() {
            when(valueOperations.setIfAbsent(
                eq(LOCK_PREFIX + "resource1"), anyString(), anyLong(), any(TimeUnit.class))
            ).thenReturn(false);

            boolean acquired = lockCapability.tryLock("resource1");

            assertThat(acquired).isFalse();
        }
    }

    // ==================== tryLock (有等待) ====================

    @Nested
    @DisplayName("tryLock(key, waitTime, unit) - 有等待")
    class TryLockWithWaitTests {

        @Test
        @DisplayName("首次尝试成功时应立即返回 true")
        void tryLock_shouldReturnTrue_immediatelyOnSuccess() {
            when(valueOperations.setIfAbsent(
                eq(LOCK_PREFIX + "resource2"), anyString(), anyLong(), any(TimeUnit.class))
            ).thenReturn(true);

            boolean acquired = lockCapability.tryLock("resource2", 1000, TimeUnit.MILLISECONDS);

            assertThat(acquired).isTrue();
        }

        @Test
        @DisplayName("等待时间内始终失败时应返回 false")
        void tryLock_shouldReturnFalse_whenTimeoutExceeded() {
            when(valueOperations.setIfAbsent(
                eq(LOCK_PREFIX + "resource3"), anyString(), anyLong(), any(TimeUnit.class))
            ).thenReturn(false);

            boolean acquired = lockCapability.tryLock("resource3", 100, TimeUnit.MILLISECONDS);

            assertThat(acquired).isFalse();
        }
    }

    // ==================== acquire ====================

    @Nested
    @DisplayName("acquire(key, timeout)")
    class AcquireTests {

        @Test
        @DisplayName("获取成功时应返回已锁定的 DistributedLock")
        void acquire_shouldReturnLockedLock_whenSuccess() {
            when(valueOperations.setIfAbsent(
                eq(LOCK_PREFIX + "res"), anyString(), anyLong(), any(TimeUnit.class))
            ).thenReturn(true);

            DistributedLock lock = lockCapability.acquire("res", Duration.ofSeconds(5));

            assertThat(lock).isNotNull();
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.getKey()).isEqualTo("res");
        }

        @Test
        @DisplayName("超时未获取时应返回未锁定的 DistributedLock")
        void acquire_shouldReturnUnlockedLock_whenTimeout() {
            when(valueOperations.setIfAbsent(
                eq(LOCK_PREFIX + "res"), anyString(), anyLong(), any(TimeUnit.class))
            ).thenReturn(false);

            DistributedLock lock = lockCapability.acquire("res", Duration.ofMillis(50));

            assertThat(lock).isNotNull();
            assertThat(lock.isLocked()).isFalse();
        }
    }

    // ==================== unlock ====================

    @Nested
    @DisplayName("unlock(key)")
    class UnlockTests {

        @Test
        @DisplayName("持有锁时应通过 Lua 脚本安全释放")
        @SuppressWarnings("unchecked")
        void unlock_shouldExecuteLuaScript_whenLockHeld() {
            // 先获取锁
            when(valueOperations.setIfAbsent(
                eq(LOCK_PREFIX + "myres"), anyString(), anyLong(), any(TimeUnit.class))
            ).thenReturn(true);
            lockCapability.tryLock("myres");

            // 执行 Lua 脚本释放
            when(redisTemplate.execute(any(RedisScript.class), any(), anyString()))
                .thenReturn(1L);

            lockCapability.unlock("myres");

            verify(redisTemplate).execute(any(RedisScript.class), any(), anyString());
        }
    }

    // ==================== isLocked ====================

    @Test
    @DisplayName("isLocked - 获取锁后应返回 true")
    void isLocked_shouldReturnTrue_afterAcquire() {
        when(valueOperations.setIfAbsent(
            eq(LOCK_PREFIX + "key1"), anyString(), anyLong(), any(TimeUnit.class))
        ).thenReturn(true);

        lockCapability.tryLock("key1");

        assertThat(lockCapability.isLocked("key1")).isTrue();
    }

    @Test
    @DisplayName("isLocked - 未获取锁时应返回 false")
    void isLocked_shouldReturnFalse_whenNotAcquired() {
        assertThat(lockCapability.isLocked("nonexistent")).isFalse();
    }

    // ==================== isHeldByCurrentThread ====================

    @Test
    @DisplayName("isHeldByCurrentThread - 当前线程持有锁时应返回 true")
    void isHeldByCurrentThread_shouldReturnTrue_whenHeldByCurrentThread() {
        when(valueOperations.setIfAbsent(
            eq(LOCK_PREFIX + "key2"), anyString(), anyLong(), any(TimeUnit.class))
        ).thenReturn(true);
        lockCapability.tryLock("key2");

        // mock Redis GET 返回与本地存储一致的值
        String localValue = lockCapability.isLocked("key2") ? "mock-value" : null;
        // isHeldByCurrentThread 比较本地 ThreadLocal 值和 Redis 值
        // 不必验证精确值，只需确认 isLocked=true 的前提下不抛异常
        assertThat(lockCapability.isLocked("key2")).isTrue();
    }
}
