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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * 基于内存的状态存储能力实现
 * 
 * <p>本类是 {@link StateStoreCapability} 的轻量级内存实现，基于 Caffeine 高性能缓存。
 * 适用于本地开发和测试场景，无需依赖 Redis 等外部存储服务。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>高性能</b>：基于 Caffeine 实现，提供接近 ConcurrentHashMap 的性能</li>
 *   <li><b>TTL 支持</b>：支持为每个键设置独立的过期时间</li>
 *   <li><b>最大容量限制</b>：防止内存溢出</li>
 *   <li><b>统计信息</b>：可选开启命中率等统计</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * InMemoryStateStoreCapability stateStore = new InMemoryStateStoreCapability();
 * 
 * // 存储数据（带 TTL）
 * stateStore.put("user:123", userInfo, Duration.ofMinutes(30));
 * 
 * // 读取数据
 * Optional<UserInfo> info = stateStore.get("user:123", UserInfo.class);
 * }</pre>
 * 
 * <h3>限制说明</h3>
 * <ul>
 *   <li>数据仅存储在当前 JVM 内存中，进程重启后丢失</li>
 *   <li>不支持跨进程/跨节点的数据共享</li>
 *   <li>受限于 JVM 堆内存大小</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see StateStoreCapability
 */
@Capability(
    type = StateStoreCapability.class,
    name = "in-memory-state-store",
    description = "基于 Caffeine 缓存的内存状态存储实现",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleStateStore", "inMemoryStateStore"}
)
public class InMemoryStateStoreCapability implements StateStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStateStoreCapability.class);

    /**
     * 默认最大缓存条目数
     */
    private static final int DEFAULT_MAX_SIZE = 10_000;

    /**
     * 默认过期时间
     */
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /**
     * Caffeine 缓存实例
     * 
     * <p>使用自定义的过期策略，支持为每个键设置独立的 TTL</p>
     */
    private final Cache<String, CacheEntry> cache;

    /**
     * TTL 映射表（键 -> 过期时间纳秒）
     */
    private final ConcurrentMap<String, Long> ttlMap = new ConcurrentHashMap<>();

    /**
     * 最大缓存容量
     */
    private final int maxSize;

    /**
     * 默认 TTL
     */
    private final Duration defaultTtl;

    /**
     * 创建内存状态存储（默认配置）
     * 
     * <p>使用默认最大容量 10000，默认 TTL 1小时。</p>
     */
    public InMemoryStateStoreCapability() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL);
    }

    /**
     * 创建内存状态存储
     * 
     * @param maxSize    最大缓存条目数
     * @param defaultTtl 默认过期时间
     */
    public InMemoryStateStoreCapability(int maxSize, Duration defaultTtl) {
        this.maxSize = maxSize;
        this.defaultTtl = defaultTtl != null ? defaultTtl : DEFAULT_TTL;
        
        // 构建 Caffeine 缓存，使用自定义过期策略
        this.cache = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfter(new Expiry<String, CacheEntry>() {
                @Override
                public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
                    // 使用存储时指定的 TTL
                    Long ttlNanos = ttlMap.get(key);
                    return ttlNanos != null ? ttlNanos : InMemoryStateStoreCapability.this.defaultTtl.toNanos();
                }

                @Override
                public long expireAfterUpdate(String key, CacheEntry value, 
                        long currentTime, long currentDuration) {
                    // 更新时重置 TTL
                    Long ttlNanos = ttlMap.get(key);
                    return ttlNanos != null ? ttlNanos : currentDuration;
                }

                @Override
                public long expireAfterRead(String key, CacheEntry value, 
                        long currentTime, long currentDuration) {
                    // 读取时不改变 TTL
                    return currentDuration;
                }
            })
            .removalListener((key, value, cause) -> {
                if (key != null) {
                    ttlMap.remove(key);
                    log.debug("缓存条目移除: key={}, cause={}", key, cause);
                }
            })
            .recordStats()
            .build();

        log.info("内存状态存储已创建: maxSize={}, defaultTtl={}", maxSize, defaultTtl);
    }

    /**
     * 获取存储的值
     * 
     * @param key  存储键，不能为空
     * @param type 值的类型
     * @param <T>  值类型
     * @return 存储的值，如果不存在或类型不匹配返回 {@link Optional#empty()}
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(type, "type 不能为空");

        CacheEntry entry = cache.getIfPresent(key);
        
        if (entry == null) {
            log.debug("状态存储读取: key={}, 结果=不存在", key);
            return Optional.empty();
        }

        // 类型检查
        if (!type.isInstance(entry.getValue())) {
            log.warn("状态存储类型不匹配: key={}, expected={}, actual={}", 
                key, type.getName(), entry.getValue().getClass().getName());
            return Optional.empty();
        }

        log.debug("状态存储读取: key={}, 类型={}", key, type.getSimpleName());
        return Optional.of(type.cast(entry.getValue()));
    }

    /**
     * 存储值（使用默认 TTL）
     * 
     * @param key   存储键，不能为空
     * @param value 要存储的值，不能为 null
     */
    @Override
    public void put(String key, Object value) {
        put(key, value, defaultTtl);
    }

    /**
     * 存储值（指定 TTL）
     * 
     * @param key   存储键，不能为空
     * @param value 要存储的值，不能为 null
     * @param ttl   过期时间
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(value, "value 不能为空");

        Duration effectiveTtl = ttl != null ? ttl : defaultTtl;
        
        // 存储 TTL 信息
        ttlMap.put(key, effectiveTtl.toNanos());
        
        // 存储值
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
        
        log.debug("状态存储写入: key={}, type={}, ttl={}", 
            key, value.getClass().getSimpleName(), effectiveTtl);
    }

    /**
     * 删除存储的值
     * 
     * @param key 存储键，不能为空
     */
    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key 不能为空");
        
        cache.invalidate(key);
        ttlMap.remove(key);
        
        log.debug("状态存储删除: key={}", key);
    }

    /**
     * 检查键是否存在
     * 
     * @param key 存储键，不能为空
     * @return 如果键存在返回 true
     */
    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key 不能为空");
        return cache.getIfPresent(key) != null;
    }

    /**
     * 获取当前缓存大小
     * 
     * @return 缓存条目数
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 统计信息字符串
     */
    public String getStats() {
        var stats = cache.stats();
        return String.format(
            "hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d", 
            stats.hitCount(), 
            stats.missCount(),
            stats.hitRate() * 100,
            stats.evictionCount()
        );
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        cache.invalidateAll();
        ttlMap.clear();
        log.info("内存状态存储已清空");
    }

    // ==================== 内部类 ====================

    /**
     * 缓存条目包装类
     */
    private static class CacheEntry {
        private final Object value;
        private final long createdAt;

        CacheEntry(Object value, long createdAt) {
            this.value = value;
            this.createdAt = createdAt;
        }

        Object getValue() {
            return value;
        }

        long getCreatedAt() {
            return createdAt;
        }
    }
}
