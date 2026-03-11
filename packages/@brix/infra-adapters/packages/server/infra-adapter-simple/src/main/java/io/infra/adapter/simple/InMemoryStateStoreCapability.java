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
 * In-Memory State Store Capability Implementation
 * 
 * <p>This class is a lightweight in-memory implementation of {@link StateStoreCapability},
 * based on Caffeine high-performance cache. Suitable for local development and testing
 * scenarios without requiring external storage services like Redis.</p>
 * 
 * <h3>Key Features</h3>
 * <ul>
 *   <li><b>High Performance</b>: Based on Caffeine implementation, provides performance close to ConcurrentHashMap</li>
 *   <li><b>TTL Support</b>: Supports independent expiration time for each key</li>
 *   <li><b>Maximum Capacity Limit</b>: Prevents memory overflow</li>
 *   <li><b>Statistics</b>: Optional hit rate and other statistics</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * InMemoryStateStoreCapability stateStore = new InMemoryStateStoreCapability();
 * 
 * // Store data (with TTL)
 * stateStore.put("user:123", userInfo, Duration.ofMinutes(30));
 * 
 * // Read data
 * Optional<UserInfo> info = stateStore.get("user:123", UserInfo.class);
 * }</pre>
 * 
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Data is stored only in current JVM memory, lost after process restart</li>
 *   <li>Cross-process/cross-node data sharing is not supported</li>
 *   <li>Limited by JVM heap memory size</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see StateStoreCapability
 */
@Capability(
    type = StateStoreCapability.class,
    name = "in-memory-state-store",
    description = "In-memory state store implementation based on Caffeine cache",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleStateStore", "inMemoryStateStore"}
)
public class InMemoryStateStoreCapability implements StateStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStateStoreCapability.class);

    /**
     * Default maximum cache entries
     */
    private static final int DEFAULT_MAX_SIZE = 10_000;

    /**
     * Default expiration time
     */
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /**
     * Caffeine cache instance
     * 
     * <p>Uses custom expiration policy to support independent TTL for each key</p>
     */
    private final Cache<String, CacheEntry> cache;

    /**
     * TTL mapping (key -> expiration time in nanoseconds)
     */
    private final ConcurrentMap<String, Long> ttlMap = new ConcurrentHashMap<>();

    /**
     * Maximum cache capacity
     */
    private final int maxSize;

    /**
     * Default TTL
     */
    private final Duration defaultTtl;

    /**
     * Creates an in-memory state store (default configuration)
     * 
     * <p>Uses default maximum capacity of 10000 and default TTL of 1 hour.</p>
     */
    public InMemoryStateStoreCapability() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL);
    }

    /**
     * Creates an in-memory state store
     * 
     * @param maxSize    Maximum cache entries
     * @param defaultTtl Default expiration time
     */
    public InMemoryStateStoreCapability(int maxSize, Duration defaultTtl) {
        this.maxSize = maxSize;
        this.defaultTtl = defaultTtl != null ? defaultTtl : DEFAULT_TTL;
        
        // Build Caffeine cache with custom expiration policy
        this.cache = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfter(new Expiry<String, CacheEntry>() {
                @Override
                public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
                    // Use TTL specified at storage time
                    Long ttlNanos = ttlMap.get(key);
                    return ttlNanos != null ? ttlNanos : InMemoryStateStoreCapability.this.defaultTtl.toNanos();
                }

                @Override
                public long expireAfterUpdate(String key, CacheEntry value, 
                        long currentTime, long currentDuration) {
                    // Reset TTL on update
                    Long ttlNanos = ttlMap.get(key);
                    return ttlNanos != null ? ttlNanos : currentDuration;
                }

                @Override
                public long expireAfterRead(String key, CacheEntry value, 
                        long currentTime, long currentDuration) {
                    // Do not change TTL on read
                    return currentDuration;
                }
            })
            .removalListener((key, value, cause) -> {
                if (key != null) {
                    ttlMap.remove(key);
                    log.debug("Cache entry removed: key={}, cause={}", key, cause);
                }
            })
            .recordStats()
            .build();

        log.info("In-memory state store created: maxSize={}, defaultTtl={}", maxSize, defaultTtl);
    }

    /**
     * Gets the stored value
     * 
     * @param key  Storage key, cannot be empty
     * @param type Value type
     * @param <T>  Value type
     * @return Stored value, returns {@link Optional#empty()} if not found or type mismatch
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        CacheEntry entry = cache.getIfPresent(key);
        
        if (entry == null) {
            log.debug("State store read: key={}, result=not found", key);
            return Optional.empty();
        }

        // Type check
        if (!type.isInstance(entry.getValue())) {
            log.warn("State store type mismatch: key={}, expected={}, actual={}", 
                key, type.getName(), entry.getValue().getClass().getName());
            return Optional.empty();
        }

        log.debug("State store read: key={}, type={}", key, type.getSimpleName());
        return Optional.of(type.cast(entry.getValue()));
    }

    /**
     * Stores a value (using default TTL)
     * 
     * @param key   Storage key, cannot be empty
     * @param value Value to store, cannot be null
     */
    @Override
    public void put(String key, Object value) {
        put(key, value, defaultTtl);
    }

    /**
     * Stores a value (with specified TTL)
     * 
     * @param key   Storage key, cannot be empty
     * @param value Value to store, cannot be null
     * @param ttl   Expiration time
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        Duration effectiveTtl = ttl != null ? ttl : defaultTtl;
        
        // Store TTL information
        ttlMap.put(key, effectiveTtl.toNanos());
        
        // Store value
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
        
        log.debug("State store write: key={}, type={}, ttl={}", 
            key, value.getClass().getSimpleName(), effectiveTtl);
    }

    /**
     * Deletes a stored value
     * 
     * @param key Storage key, cannot be empty
     */
    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        
        cache.invalidate(key);
        ttlMap.remove(key);
        
        log.debug("State store delete: key={}", key);
    }

    /**
     * Checks if a key exists
     * 
     * @param key Storage key, cannot be empty
     * @return true if the key exists
     */
    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return cache.getIfPresent(key) != null;
    }

    /**
     * Gets the current cache size
     * 
     * @return Number of cache entries
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * Gets cache statistics
     * 
     * @return Statistics string
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
     * Clears all cache entries
     */
    public void clear() {
        cache.invalidateAll();
        ttlMap.clear();
        log.info("In-memory state store cleared");
    }

    // ==================== Inner Classes ====================

    /**
     * Cache entry wrapper class
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
