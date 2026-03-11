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
package io.infra.adapter.simple;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.DistributedLock;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * In-Memory Distributed Lock Capability Implementation
 * 
 * <p>This class is a lightweight in-memory implementation of {@link LockCapability},
 * based on Java ReentrantLock. Suitable for local development and testing scenarios
 * without requiring external storage like Redis.</p>
 * 
 * <h3>Key Features</h3>
 * <ul>
 *   <li><b>Reentrant</b>: Same thread can acquire the same lock multiple times</li>
 *   <li><b>Fair Lock</b>: Configurable fair mode</li>
 *   <li><b>Timeout Wait</b>: Supports lock acquisition with timeout</li>
 *   <li><b>Auto-Release</b>: Supports try-with-resources automatic release</li>
 * </ul>
 * 
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Lock is only valid within the current JVM, cross-process is not supported</li>
 *   <li>Lock is automatically released after process restart</li>
 *   <li>Mutual exclusion in distributed environments is not supported</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see LockCapability
 */
@Capability(
    type = LockCapability.class,
    name = "in-memory-lock",
    description = "In-memory distributed lock implementation based on ReentrantLock",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleLock", "inMemoryLock"}
)
public class InMemoryLockCapability implements LockCapability {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLockCapability.class);

    /**
     * Lock mapping (key -> ReentrantLock)
     */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Lock owner mapping (key -> threadId)
     */
    private final Map<String, Long> lockOwners = new ConcurrentHashMap<>();

    /**
     * Whether to use fair lock
     */
    private final boolean fair;

    /**
     * Creates in-memory lock capability (unfair lock)
     */
    public InMemoryLockCapability() {
        this(false);
    }

    /**
     * Creates in-memory lock capability
     * 
     * @param fair Whether to use fair lock
     */
    public InMemoryLockCapability(boolean fair) {
        this.fair = fair;
        log.info("In-memory lock capability created: fair={}", fair);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributedLock acquire(String key, Duration timeout) {
        Objects.requireNonNull(key, "Lock key cannot be null");
        Objects.requireNonNull(timeout, "Timeout cannot be null");

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(fair));
        
        try {
            boolean acquired = lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                lockOwners.put(key, Thread.currentThread().getId());
                log.debug("Lock acquired successfully: key={}, threadId={}", key, Thread.currentThread().getId());
            } else {
                log.debug("Lock acquisition timeout: key={}, timeout={}", key, timeout);
            }
            return new InMemoryDistributedLock(key, lock, acquired);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: key={}", key);
            return new InMemoryDistributedLock(key, lock, false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryLock(String key) {
        Objects.requireNonNull(key, "Lock key cannot be null");

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(fair));
        boolean acquired = lock.tryLock();
        
        if (acquired) {
            lockOwners.put(key, Thread.currentThread().getId());
            log.debug("Try lock succeeded: key={}", key);
        } else {
            log.debug("Try lock failed: key={}", key);
        }
        
        return acquired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryLock(String key, long waitTime, TimeUnit unit) {
        Objects.requireNonNull(key, "Lock key cannot be null");
        Objects.requireNonNull(unit, "Time unit cannot be null");

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(fair));
        
        try {
            boolean acquired = lock.tryLock(waitTime, unit);
            if (acquired) {
                lockOwners.put(key, Thread.currentThread().getId());
                log.debug("Lock acquired successfully: key={}, waitTime={} {}", key, waitTime, unit);
            } else {
                log.debug("Lock acquisition timeout: key={}, waitTime={} {}", key, waitTime, unit);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: key={}", key);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlock(String key) {
        Objects.requireNonNull(key, "Lock key cannot be null");

        ReentrantLock lock = locks.get(key);
        if (lock == null) {
            log.warn("Lock does not exist: key={}", key);
            return;
        }

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            if (lock.getHoldCount() == 0) {
                lockOwners.remove(key);
            }
            log.debug("Lock released: key={}", key);
        } else {
            log.warn("Current thread does not hold lock: key={}, currentThread={}", key, Thread.currentThread().getId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLocked(String key) {
        ReentrantLock lock = locks.get(key);
        return lock != null && lock.isLocked();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHeldByCurrentThread(String key) {
        ReentrantLock lock = locks.get(key);
        return lock != null && lock.isHeldByCurrentThread();
    }

    /**
     * Gets current lock count
     * 
     * @return Lock count
     */
    public int getLockCount() {
        return (int) locks.values().stream()
                .filter(ReentrantLock::isLocked)
                .count();
    }

    /**
     * Clears all locks (for testing only)
     */
    public void clearAll() {
        locks.clear();
        lockOwners.clear();
        log.info("All locks cleared");
    }

    /**
     * In-memory distributed lock implementation
     */
    private class InMemoryDistributedLock implements DistributedLock {
        
        private final String key;
        private final ReentrantLock lock;
        private final boolean locked;
        private volatile boolean released = false;

        InMemoryDistributedLock(String key, ReentrantLock lock, boolean locked) {
            this.key = key;
            this.lock = lock;
            this.locked = locked;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public boolean isLocked() {
            return locked && !released && lock.isHeldByCurrentThread();
        }

        @Override
        public void release() {
            if (locked && !released && lock.isHeldByCurrentThread()) {
                lock.unlock();
                released = true;
                if (lock.getHoldCount() == 0) {
                    lockOwners.remove(key);
                }
                log.debug("Lock released: key={}", key);
            }
        }

        @Override
        public void close() {
            release();
        }
    }
}
