/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.cache;

import io.runtime.sdk.capability.StateStoreCapability;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Spring {@link CacheManager} implementation backed by the Runtime Shell's
 * {@link StateStoreCapability}.
 *
 * <p>This cache manager bridges the standard Spring Cache abstraction to the
 * platform's capability-based state store, enabling plugin services to use
 * {@code @Cacheable}, {@code @CacheEvict}, and {@code @CachePut} annotations
 * transparently, with the actual cache storage delegated to whichever
 * StateStoreCapability implementation the Host has assembled (Redis, Caffeine,
 * in-memory, etc.).</p>
 *
 * <h3>Architecture Position</h3>
 * <pre>
 * Plugin Service (@Cacheable)
 *     ↓ Spring Cache interceptor
 * StateStoreCacheManager (this class, Layer 2C — platform-common-starter)
 *     ↓ delegates to
 * StateStoreCapability (Layer 2A — runtime-sdk-api contract)
 *     ↓ implemented by
 * RedisStateStoreCapability / InMemoryStateStoreCapability (Layer 2C — infra-adapters)
 * </pre>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Dynamic cache creation:</b> Cache regions are created on demand when first
 *       accessed via {@code @Cacheable(cacheNames = "xxx")}. No pre-registration
 *       required.</li>
 *   <li><b>Uniform TTL:</b> All cache regions share a configurable default TTL
 *       (see {@link PlatformCacheProperties}). Per-region TTL can be extended in
 *       future versions.</li>
 *   <li><b>Thread safety:</b> Cache instances are stored in a {@link ConcurrentHashMap}
 *       and created atomically via {@code computeIfAbsent}.</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.0.9
 * @see StateStoreCache
 * @see StateStoreCapability
 * @see io.brix.platform.starter.autoconfigure.PlatformCacheAutoConfiguration
 */
public class StateStoreCacheManager implements CacheManager {

    /**
     * Thread-safe registry of cache instances, keyed by cache name.
     */
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    /**
     * The Runtime Shell state store capability that backs all caches.
     */
    private final StateStoreCapability stateStore;

    /**
     * Default TTL applied to all cache entries.
     */
    private final Duration defaultTtl;

    /**
     * Creates a new StateStoreCacheManager.
     *
     * @param stateStore the Runtime Shell state store capability (must not be null)
     * @param defaultTtl the default TTL for cache entries (must not be null or negative)
     * @throws NullPointerException if stateStore or defaultTtl is null
     */
    public StateStoreCacheManager(StateStoreCapability stateStore, Duration defaultTtl) {
        this.stateStore = java.util.Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.defaultTtl = java.util.Objects.requireNonNull(defaultTtl, "defaultTtl must not be null");
    }

    /**
     * Returns the cache for the given name, creating it on demand if it does not exist.
     *
     * <p>Cache instances are created lazily and cached in a thread-safe map. The
     * underlying storage (via {@link StateStoreCapability}) uses namespaced keys
     * to isolate different cache regions.</p>
     *
     * @param name the cache region name (e.g., "users", "products")
     * @return the cache instance, never {@code null}
     */
    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, n -> new StateStoreCache(n, stateStore, defaultTtl));
    }

    /**
     * Returns the names of all known cache regions.
     *
     * <p>Only returns caches that have been explicitly accessed. This does not
     * scan the state store for existing keys.</p>
     *
     * @return unmodifiable collection of cache names
     */
    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }
}
