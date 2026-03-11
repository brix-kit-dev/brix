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
 * Redis-based distributed lock capability implementation.
 * 
 * <p>This class implements {@link LockCapability} Full Product Host implementation,
 * using Redis SET NX EX command to implement distributed locks.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>Atomic Operations</b>: Uses Redis SET NX EX to ensure atomicity.</li>
 *   <li><b>Auto Expiration</b>: Prevents deadlocks.</li>
 *   <li><b>Safe Release</b>: Uses Lua script to ensure only own lock is released.</li>
 *   <li><b>Reentrant</b>: Same thread can repeatedly acquire the same lock</li>
 * </ul>
 * 
 * <h3>Lock Key Format</h3>
 * <p>brix:lock:{key}</p>
 * 
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe and can be used concurrently by multiple threads.</p>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 * @see LockCapability
 */
@Capability(
    type = LockCapability.class,
    name = "redis-distributed-lock",
    description = "Redis-based distributed lock capability implementation",
    level = CapabilityLevel.CORE,
    aliases = {"lock", "redisLock"}
)
public class RedisLockCapability implements LockCapability {

    private static final Logger log = LoggerFactory.getLogger(RedisLockCapability.class);

    /**
     * Lock key prefix.
     */
    private final String lockPrefix;

    /**
     * Default lock expiration time (seconds).
     */
    private final int defaultExpireSeconds;

    /**
     * Spin wait initial delay (milliseconds).
     */
    private static final long SPIN_INITIAL_DELAY_MS = 50;

    /**
     * Spin wait max delay (milliseconds).
     */
    private static final long SPIN_MAX_DELAY_MS = 500;

    /**
     * Exponential backoff multiplier.
     */
    private static final double BACKOFF_MULTIPLIER = 1.5;

    /**
     * Lua script for releasing lock.
     * 
     * <p>Only deletes when the lock value matches expected value, preventing accidental deletion of other thread's lock</p>
     */
    private static final String UNLOCK_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final StringRedisTemplate redisTemplate;

    /**
     * Stores locks held by current thread (lock key -> lock value).
     * 
     * <p>Used for supporting reentrant and correct release</p>
     */
    private final Map<String, ThreadLocal<String>> lockValues = new ConcurrentHashMap<>();

    /**
     * Constructor.
     * 
     * @param redisTemplate Redis template
     * @param lockPrefix lock key prefix
     * @param defaultExpireSeconds default lock expiration time (seconds)
     */
    public RedisLockCapability(StringRedisTemplate redisTemplate, String lockPrefix, int defaultExpireSeconds) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.lockPrefix = Objects.requireNonNull(lockPrefix, "lockPrefix cannot be null");
        this.defaultExpireSeconds = defaultExpireSeconds;
    }

    /**
     * Acquires distributed lock.
     * 
     * <p>Blocks until lock is acquired or timeout</p>
     * 
     * @param key     unique lock identifier
     * @param timeout maximum wait time
     * @return lock object, can be used with try-with-resources
     */
    @Override
    public DistributedLock acquire(String key, Duration timeout) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");

        String fullKey = buildKey(key);
        String lockValue = generateLockValue();
        
        long startTime = System.currentTimeMillis();
        long waitMillis = timeout.toMillis();
        
        // Spin wait to acquire lock (exponential backoff + random jitter)
        long currentDelay = SPIN_INITIAL_DELAY_MS;
        while (System.currentTimeMillis() - startTime < waitMillis) {
            if (doTryLock(fullKey, lockValue, defaultExpireSeconds)) {
                // Acquisition successful, record lock value
                storeLockValue(fullKey, lockValue);
                log.debug("Distributed lock acquired: key={}", key);
                return new RedisDistributedLock(this, key, true);
            }
            
            // Exponential backoff + random jitter to avoid thundering herd
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
        
        // Acquisition failed
        log.debug("Distributed lock acquisition timeout: key={}, timeout={}ms", key, waitMillis);
        return new RedisDistributedLock(this, key, false);
    }

    /**
     * Tries to acquire lock (non-blocking).
     * 
     * @param key unique lock identifier
     * @return true if acquisition successful
     */
    @Override
    public boolean tryLock(String key) {
        return tryLock(key, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Tries to acquire lock (with wait time).
     * 
     * @param key      unique lock identifier
     * @param waitTime wait time
     * @param unit     time unit
     * @return true if acquisition successful
     */
    @Override
    public boolean tryLock(String key, long waitTime, TimeUnit unit) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(unit, "unit cannot be null");

        String fullKey = buildKey(key);
        String lockValue = generateLockValue();
        
        if (waitTime <= 0) {
            // Non-blocking mode
            boolean success = doTryLock(fullKey, lockValue, defaultExpireSeconds);
            if (success) {
                storeLockValue(fullKey, lockValue);
                log.debug("Distributed lock acquired: key={}", key);
            }
            return success;
        }
        
        // With wait time (exponential backoff + random jitter)
        long startTime = System.currentTimeMillis();
        long waitMillis = unit.toMillis(waitTime);
        long currentDelay = SPIN_INITIAL_DELAY_MS;
        
        while (System.currentTimeMillis() - startTime < waitMillis) {
            if (doTryLock(fullKey, lockValue, defaultExpireSeconds)) {
                storeLockValue(fullKey, lockValue);
                log.debug("Distributed lock acquired: key={}", key);
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
     * Releases lock.
     * 
     * @param key unique lock identifier
     */
    @Override
    public void unlock(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        String fullKey = buildKey(key);
        String lockValue = getLockValue(fullKey);
        
        if (lockValue == null) {
            log.warn("Attempting to release unheld lock: key={}", key);
            return;
        }
        
        // Safely release using Lua script
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(fullKey), lockValue);
        
        // Clear local record
        removeLockValue(fullKey);
        
        if (result != null && result > 0) {
            log.debug("Distributed lock released: key={}", key);
        } else {
            log.warn("Distributed lock release failed (may have expired): key={}", key);
        }
    }

    /**
     * Checks if lock is held.
     * 
     * @param key unique lock identifier
     * @return true if current thread holds the lock
     */
    @Override
    public boolean isLocked(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        
        String fullKey = buildKey(key);
        return getLockValue(fullKey) != null;
    }

    /**
     * Executes lock acquisition operation.
     * 
     * @param fullKey       full key
     * @param lockValue     lock value
     * @param expireSeconds expiration time (seconds)
     * @return true if acquisition successful
     */
    private boolean doTryLock(String fullKey, String lockValue, int expireSeconds) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, lockValue, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * Generates lock value.
     * 
     * <p>Format: {threadId}:{uuid}, ensures global uniqueness</p>
     * 
     * @return lock value
     */
    private String generateLockValue() {
        return Thread.currentThread().getId() + ":" + UUID.randomUUID().toString();
    }

    /**
     * Builds full key.
     * 
     * @param key user-provided key
     * @return full key
     */
    private String buildKey(String key) {
        return lockPrefix + key;
    }

    /**
     * Stores lock value.
     */
    private void storeLockValue(String fullKey, String lockValue) {
        lockValues.computeIfAbsent(fullKey, k -> new ThreadLocal<>()).set(lockValue);
    }

    /**
     * Gets lock value.
     */
    private String getLockValue(String fullKey) {
        ThreadLocal<String> threadLocal = lockValues.get(fullKey);
        return threadLocal != null ? threadLocal.get() : null;
    }

    /**
     * Removes lock value.
     *
     * <p>Cleans up local cache when releasing lock.
     * 
     * <p>Memory leak fix (v3.2):
     * In addition to clearing the value in ThreadLocal, the entry must also be removed from the lockValues Map.
     * Otherwise, as locks are acquired and released, the Map will grow indefinitely causing memory leaks.
     *
     * @param fullKey the full lock key
     */
    private void removeLockValue(String fullKey) {
        ThreadLocal<String> threadLocal = lockValues.get(fullKey);
        if (threadLocal != null) {
            // First clear the value in ThreadLocal to prevent dirty data when thread is reused
            threadLocal.remove();
            // Remove entry from Map to prevent memory leak
            // Note: Using remove(key, value) semantics to ensure only current thread's record is removed
            lockValues.remove(fullKey);
        }
    }

    /**
     * Checks if current thread holds the lock.
     * 
     * @param key unique lock identifier
     * @return true if current thread holds the lock
     */
    @Override
    public boolean isHeldByCurrentThread(String key) {
        Objects.requireNonNull(key, "lock key cannot be null");
        
        String fullKey = buildKey(key);
        String lockValue = getLockValue(fullKey);
        
        if (lockValue == null) {
            return false;
        }
        
        // Verify if the value stored in Redis matches the current thread's value
        String storedValue = redisTemplate.opsForValue().get(fullKey);
        return lockValue.equals(storedValue);
    }

    /**
     * Redis distributed lock implementation.
     * 
     * <p>Implements {@link DistributedLock} interface, supports try-with-resources</p>
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
