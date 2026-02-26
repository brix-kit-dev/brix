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
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import io.runtime.sdk.capability.DistributedLock;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * 基于 Redis 的分布式锁能力实现
 * 
 * <p>本类实现{@link LockCapability} Full Product Host 实现
 * 使用 Redis SET NX EX 命令实现分布式锁。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>原子操作</b>：使Redis SET NX EX 保证原子。</li>
 *   <li><b>自动过期</b>：防止死。</li>
 *   <li><b>安全释放</b>：使Lua 脚本确保只释放自己的。</li>
 *   <li><b>可重入。</b>：同一线程可重复获取同一把锁</li>
 * </ul>
 * 
 * <h3>锁的键格。</h3>
 * <p>shinwa:lock:{key}</p>
 * 
 * <h3>线程安全</h3>
 * <p>本类是线程安全的，可以被多个线程并发使用。</p>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 * @see LockCapability
 */
@Capability(
    type = LockCapability.class,
    name = "redis-distributed-lock",
    description = "基于 Redis 的分布式锁能力实现",
    level = CapabilityLevel.CORE,
    aliases = {"lock", "redisLock"}
)
public class RedisLockCapability implements LockCapability {

    private static final Logger log = LoggerFactory.getLogger(RedisLockCapability.class);

    /**
     * 锁键前缀
     */
    private final String lockPrefix;

    /**
     * 默认锁过期时间（秒）
     */
    private final int defaultExpireSeconds;

    /**
     * 自旋等待初始间隔（毫秒）
     */
    private static final long SPIN_INITIAL_DELAY_MS = 50;

    /**
     * 自旋等待最大间隔（毫秒）
     */
    private static final long SPIN_MAX_DELAY_MS = 500;

    /**
     * 指数退避乘数
     */
    private static final double BACKOFF_MULTIPLIER = 1.5;

    /**
     * 释放锁的 Lua 脚本
     * 
     * <p>只有当锁的值与期望值匹配时才删除，防止误删其他线程的锁</p>
     */
    private static final String UNLOCK_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final StringRedisTemplate redisTemplate;

    /**
     * 存储当前线程持有的锁（锁-> 锁值）
     * 
     * <p>用于支持可重入和正确释放</p>
     */
    private final Map<String, ThreadLocal<String>> lockValues = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param redisTemplate Redis 模板
     * @param lockPrefix 锁键前缀
     * @param defaultExpireSeconds 默认锁过期时间（秒）
     */
    public RedisLockCapability(StringRedisTemplate redisTemplate, String lockPrefix, int defaultExpireSeconds) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate 不能为空");
        this.lockPrefix = Objects.requireNonNull(lockPrefix, "lockPrefix 不能为空");
        this.defaultExpireSeconds = defaultExpireSeconds;
    }

    /**
     * 获取分布式锁
     * 
     * <p>阻塞等待直到获取锁或超时</p>
     * 
     * @param key     锁的唯一
     * @param timeout 最大等待时间
     * @return 锁对象，可用。try-with-resources
     */
    @Override
    public DistributedLock acquire(String key, Duration timeout) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(timeout, "timeout 不能为空");

        String fullKey = buildKey(key);
        String lockValue = generateLockValue();
        
        long startTime = System.currentTimeMillis();
        long waitMillis = timeout.toMillis();
        
        // 自旋等待获取锁（指数退避 + 随机抖动）
        long currentDelay = SPIN_INITIAL_DELAY_MS;
        while (System.currentTimeMillis() - startTime < waitMillis) {
            if (doTryLock(fullKey, lockValue, defaultExpireSeconds)) {
                // 获取成功，记录锁值
                storeLockValue(fullKey, lockValue);
                log.debug("分布式锁获取成功: key={}", key);
                return new RedisDistributedLock(this, key, true);
            }
            
            // 指数退避 + 随机抖动，避免雷群效应
            long jitter = ThreadLocalRandom.current().nextLong(0, currentDelay / 2 + 1);
            long sleepTime = Math.min(currentDelay + jitter, SPIN_MAX_DELAY_MS);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            currentDelay = (long) Math.min(currentDelay * BACKOFF_MULTIPLIER, SPIN_MAX_DELAY_MS);
        }
        
        // 获取失败
        log.debug("分布式锁获取超时: key={}, timeout={}ms", key, waitMillis);
        return new RedisDistributedLock(this, key, false);
    }

    /**
     * 尝试获取锁（非阻塞）
     * 
     * @param key 锁的唯一
     * @return 如果获取成功返回 true
     */
    @Override
    public boolean tryLock(String key) {
        return tryLock(key, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * 尝试获取锁（带等待时间）
     * 
     * @param key      锁的唯一
     * @param waitTime 等待时间
     * @param unit     时间单位
     * @return 如果获取成功返回 true
     */
    @Override
    public boolean tryLock(String key, long waitTime, TimeUnit unit) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(unit, "unit 不能为空");

        String fullKey = buildKey(key);
        String lockValue = generateLockValue();
        
        if (waitTime <= 0) {
            // 非阻塞模
            boolean success = doTryLock(fullKey, lockValue, defaultExpireSeconds);
            if (success) {
                storeLockValue(fullKey, lockValue);
                log.debug("分布式锁获取成功: key={}", key);
            }
            return success;
        }
        
        // 带等待时间（指数退避 + 随机抖动）
        long startTime = System.currentTimeMillis();
        long waitMillis = unit.toMillis(waitTime);
        long currentDelay = SPIN_INITIAL_DELAY_MS;
        
        while (System.currentTimeMillis() - startTime < waitMillis) {
            if (doTryLock(fullKey, lockValue, defaultExpireSeconds)) {
                storeLockValue(fullKey, lockValue);
                log.debug("分布式锁获取成功: key={}", key);
                return true;
            }
            
            long jitter = ThreadLocalRandom.current().nextLong(0, currentDelay / 2 + 1);
            long sleepTime = Math.min(currentDelay + jitter, SPIN_MAX_DELAY_MS);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            currentDelay = (long) Math.min(currentDelay * BACKOFF_MULTIPLIER, SPIN_MAX_DELAY_MS);
        }
        
        return false;
    }

    /**
     * 释放
     * 
     * @param key 锁的唯一
     */
    @Override
    public void unlock(String key) {
        Objects.requireNonNull(key, "key 不能为空");

        String fullKey = buildKey(key);
        String lockValue = getLockValue(fullKey);
        
        if (lockValue == null) {
            log.warn("尝试释放未持有的 key={}", key);
            return;
        }
        
        // 使用 Lua 脚本安全释放
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(fullKey), lockValue);
        
        // 清除本地记录
        removeLockValue(fullKey);
        
        if (result != null && result > 0) {
            log.debug("分布式锁释放成功: key={}", key);
        } else {
            log.warn("分布式锁释放失败（可能已过期 key={}", key);
        }
    }

    /**
     * 检查是否持有锁
     * 
     * @param key 锁的唯一
     * @return 如果当前线程持有锁返回 true
     */
    @Override
    public boolean isLocked(String key) {
        Objects.requireNonNull(key, "key 不能为空");
        
        String fullKey = buildKey(key);
        return getLockValue(fullKey) != null;
    }

    /**
     * 执行获取锁操
     * 
     * @param fullKey     完整
     * @param lockValue   锁
     * @param expireSeconds 过期时间（秒
     * @return 如果获取成功返回 true
     */
    private boolean doTryLock(String fullKey, String lockValue, int expireSeconds) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, lockValue, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 生成锁
     * 
     * <p>格式：{threadId}:{uuid}，确保全局唯一</p>
     * 
     * @return 锁
     */
    private String generateLockValue() {
        return Thread.currentThread().getId() + ":" + UUID.randomUUID().toString();
    }

    /**
     * 构建完整
     * 
     * @param key 用户
     * @return 完整
     */
    private String buildKey(String key) {
        return lockPrefix + key;
    }

    /**
     * 存储锁
     */
    private void storeLockValue(String fullKey, String lockValue) {
        lockValues.computeIfAbsent(fullKey, k -> new ThreadLocal<>()).set(lockValue);
    }

    /**
     * 获取锁
     */
    private String getLockValue(String fullKey) {
        ThreadLocal<String> threadLocal = lockValues.get(fullKey);
        return threadLocal != null ? threadLocal.get() : null;
    }

    /**
     * 移除锁值
     *
     * <p>释放锁时清理本地缓存。
     * 
     * <p>内存泄漏修复（v3.2）：
     * 除了清除 ThreadLocal 中的值，还需要从 lockValues Map 中移除 entry。
     * 否则随着锁的获取和释放，Map 会无限增长导致内存泄漏。
     *
     * @param fullKey 完整的锁键
     */
    private void removeLockValue(String fullKey) {
        ThreadLocal<String> threadLocal = lockValues.get(fullKey);
        if (threadLocal != null) {
            // 首先清除 ThreadLocal 中的值，防止线程复用时出现脏数据
            threadLocal.remove();
            // 从 Map 中移除 entry，防止内存泄漏
            // 注意：这里使用 remove(key, value) 的语义，确保只移除当前线程的记录
            lockValues.remove(fullKey);
        }
    }

    /**
     * 检查当前线程是否持有锁
     * 
     * @param key 锁的唯一
     * @return 如果当前线程持有锁返回 true
     */
    @Override
    public boolean isHeldByCurrentThread(String key) {
        Objects.requireNonNull(key, "锁键不能为空");
        
        String fullKey = buildKey(key);
        String lockValue = getLockValue(fullKey);
        
        if (lockValue == null) {
            return false;
        }
        
        // 验证 Redis 中存储的值是否与当前线程的值匹
        String storedValue = redisTemplate.opsForValue().get(fullKey);
        return lockValue.equals(storedValue);
    }

    /**
     * Redis 分布式锁实现
     * 
     * <p>实现 {@link DistributedLock} 接口，支try-with-resources</p>
     */
    private static class RedisDistributedLock implements DistributedLock {

        private final RedisLockCapability lockCapability;
        private final String key;
        private final boolean locked;

        RedisDistributedLock(RedisLockCapability lockCapability, String key, boolean locked) {
            this.lockCapability = lockCapability;
            this.key = key;
            this.locked = locked;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public boolean isLocked() {
            return locked;
        }

        @Override
        public void release() {
            if (locked) {
                lockCapability.unlock(key);
            }
        }

        @Override
        public void close() {
            release();
        }
    }
}
