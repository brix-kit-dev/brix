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
 * 基于内存的分布式锁能力实现
 * 
 * <p>本类是 {@link LockCapability} 的轻量级内存实现，基于 Java ReentrantLock。
 * 适用于本地开发和测试场景，无需依赖 Redis 等外部存储。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>可重入</b>：同一线程可多次获取同一把锁</li>
 *   <li><b>公平锁</b>：可配置公平模式</li>
 *   <li><b>超时等待</b>：支持带超时的锁获取</li>
 *   <li><b>自动释放</b>：支持 try-with-resources 自动释放</li>
 * </ul>
 * 
 * <h3>限制说明</h3>
 * <ul>
 *   <li>锁仅在当前 JVM 内有效，不支持跨进程</li>
 *   <li>进程重启后锁自动释放</li>
 *   <li>不支持分布式环境下的互斥</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see LockCapability
 */
@Capability(
    type = LockCapability.class,
    name = "in-memory-lock",
    description = "基于 ReentrantLock 的内存分布式锁实现",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleLock", "inMemoryLock"}
)
public class InMemoryLockCapability implements LockCapability {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLockCapability.class);

    /**
     * 锁映射（key -> ReentrantLock）
     */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 锁持有者映射（key -> threadId）
     */
    private final Map<String, Long> lockOwners = new ConcurrentHashMap<>();

    /**
     * 是否使用公平锁
     */
    private final boolean fair;

    /**
     * 创建内存锁能力（非公平锁）
     */
    public InMemoryLockCapability() {
        this(false);
    }

    /**
     * 创建内存锁能力
     * 
     * @param fair 是否使用公平锁
     */
    public InMemoryLockCapability(boolean fair) {
        this.fair = fair;
        log.info("内存锁能力已创建: fair={}", fair);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributedLock acquire(String key, Duration timeout) {
        Objects.requireNonNull(key, "锁键不能为空");
        Objects.requireNonNull(timeout, "超时时间不能为空");

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(fair));
        
        try {
            boolean acquired = lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                lockOwners.put(key, Thread.currentThread().getId());
                log.debug("获取锁成功: key={}, threadId={}", key, Thread.currentThread().getId());
            } else {
                log.debug("获取锁超时: key={}, timeout={}", key, timeout);
            }
            return new InMemoryDistributedLock(key, lock, acquired);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断: key={}", key);
            return new InMemoryDistributedLock(key, lock, false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryLock(String key) {
        Objects.requireNonNull(key, "锁键不能为空");

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(fair));
        boolean acquired = lock.tryLock();
        
        if (acquired) {
            lockOwners.put(key, Thread.currentThread().getId());
            log.debug("尝试获取锁成功: key={}", key);
        } else {
            log.debug("尝试获取锁失败: key={}", key);
        }
        
        return acquired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryLock(String key, long waitTime, TimeUnit unit) {
        Objects.requireNonNull(key, "锁键不能为空");
        Objects.requireNonNull(unit, "时间单位不能为空");

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(fair));
        
        try {
            boolean acquired = lock.tryLock(waitTime, unit);
            if (acquired) {
                lockOwners.put(key, Thread.currentThread().getId());
                log.debug("获取锁成功: key={}, waitTime={} {}", key, waitTime, unit);
            } else {
                log.debug("获取锁超时: key={}, waitTime={} {}", key, waitTime, unit);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断: key={}", key);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlock(String key) {
        Objects.requireNonNull(key, "锁键不能为空");

        ReentrantLock lock = locks.get(key);
        if (lock == null) {
            log.warn("锁不存在: key={}", key);
            return;
        }

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            if (lock.getHoldCount() == 0) {
                lockOwners.remove(key);
            }
            log.debug("释放锁: key={}", key);
        } else {
            log.warn("当前线程未持有锁: key={}, currentThread={}", key, Thread.currentThread().getId());
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
     * 获取当前锁数量
     * 
     * @return 锁数量
     */
    public int getLockCount() {
        return (int) locks.values().stream()
                .filter(ReentrantLock::isLocked)
                .count();
    }

    /**
     * 清理所有锁（仅用于测试）
     */
    public void clearAll() {
        locks.clear();
        lockOwners.clear();
        log.info("清理所有锁");
    }

    /**
     * 内存分布式锁实现
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
                log.debug("释放锁: key={}", key);
            }
        }

        @Override
        public void close() {
            release();
        }
    }
}
