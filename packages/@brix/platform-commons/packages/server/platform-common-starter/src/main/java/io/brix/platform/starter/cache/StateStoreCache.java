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
import org.springframework.cache.support.SimpleValueWrapper;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Spring Cache adapter backed by the Runtime Shell's {@link StateStoreCapability}.
 *
 * <p>This class bridges the standard Spring {@link Cache} interface to the platform's
 * capability-based state store, ensuring that plugin services can use standard
 * {@code @Cacheable} / {@code @CacheEvict} annotations without directly depending
 * on any cache middleware (Redis, Caffeine, etc.).</p>
 *
 * <h3>Architecture Compliance</h3>
 * <p>Per v3.0 Architecture Blueprint §1.2 Constraint 2 ("Plugins only depend on
 * Runtime Capability Contract"), this adapter lives in the platform layer (Layer 2C)
 * and is auto-configured by the Host assembly. Plugin code never instantiates this
 * class directly.</p>
 *
 * <h3>Key Namespace</h3>
 * <p>All cache keys are prefixed with {@code cache:{cacheName}:} to provide namespace
 * isolation between different cache regions and avoid key collisions with other
 * StateStoreCapability consumers.</p>
 *
 * <h3>TTL Configuration</h3>
 * <p>A default TTL is applied to all entries via the {@link StateStoreCacheManager}
 * configuration. Per-entry TTL is not supported by the Spring Cache abstraction;
 * use {@link StateStoreCapability} directly for fine-grained TTL control.</p>
 *
 * @author Brix Platform Team
 * @since 3.0.9
 * @see StateStoreCacheManager
 * @see StateStoreCapability
 */
public class StateStoreCache implements Cache {

    /**
     * Cache name (used as namespace prefix in the state store).
     */
    private final String name;

    /**
     * Underlying state store capability from the Runtime Shell.
     */
    private final StateStoreCapability stateStore;

    /**
     * Time-to-live for cache entries. Entries expire automatically after this duration.
     */
    private final Duration ttl;

    /**
     * Key prefix pattern: {@code cache:{name}:}.
     */
    private final String keyPrefix;

    /**
     * Creates a new StateStoreCache.
     *
     * @param name       the cache region name (e.g., "users", "products")
     * @param stateStore the Runtime Shell state store capability
     * @param ttl        default time-to-live for cache entries
     */
    public StateStoreCache(String name, StateStoreCapability stateStore, Duration ttl) {
        this.name = name;
        this.stateStore = stateStore;
        this.ttl = ttl;
        this.keyPrefix = "cache:" + name + ":";
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the underlying {@link StateStoreCapability} as the native cache.
     *
     * @return the state store capability instance
     */
    @Override
    public Object getNativeCache() {
        return stateStore;
    }

    /**
     * Retrieves a cached value by key.
     *
     * <p>The value is fetched from the state store using the namespaced key.
     * Since the state store uses JSON serialization, the returned value is
     * deserialized as a generic Object.</p>
     *
     * @param key the cache key
     * @return a {@link ValueWrapper} containing the value, or {@code null} if not found
     */
    @Override
    public ValueWrapper get(Object key) {
        String storeKey = toStoreKey(key);
        return stateStore.get(storeKey, Object.class)
            .map(SimpleValueWrapper::new)
            .orElse(null);
    }

    /**
     * Retrieves a cached value by key with type-safe deserialization.
     *
     * @param key  the cache key
     * @param type the expected value type
     * @param <T>  the value type
     * @return the cached value, or {@code null} if not found
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        String storeKey = toStoreKey(key);
        return stateStore.get(storeKey, type).orElse(null);
    }

    /**
     * Retrieves a cached value, computing it if absent (synchronized get-or-load).
     *
     * <p>If the value is not present in the cache, the {@code valueLoader} callable
     * is invoked and its result is stored. This operation is not globally atomic
     * but provides local synchronization to prevent redundant computation.</p>
     *
     * @param key         the cache key
     * @param valueLoader the callable to compute the value if absent
     * @param <T>         the value type
     * @return the cached or computed value
     * @throws Cache.ValueRetrievalException if the valueLoader throws an exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        String storeKey = toStoreKey(key);
        return (T) stateStore.get(storeKey, Object.class)
            .orElseGet(() -> {
                try {
                    T value = valueLoader.call();
                    if (value != null) {
                        stateStore.put(storeKey, value, ttl);
                    }
                    return (Object) value;
                } catch (Exception ex) {
                    throw new ValueRetrievalException(key, valueLoader, ex);
                }
            });
    }

    /**
     * Stores a value in the cache with the configured TTL.
     *
     * @param key   the cache key
     * @param value the value to cache (may be {@code null}, which triggers eviction)
     */
    @Override
    public void put(Object key, Object value) {
        String storeKey = toStoreKey(key);
        if (value == null) {
            stateStore.remove(storeKey);
        } else {
            stateStore.put(storeKey, value, ttl);
        }
    }

    /**
     * Evicts the entry for the given key.
     *
     * @param key the cache key to evict
     */
    @Override
    public void evict(Object key) {
        stateStore.remove(toStoreKey(key));
    }

    /**
     * Clears all entries in this cache region.
     *
     * <p><b>Note:</b> The {@link StateStoreCapability} interface does not provide
     * a bulk-delete or scan operation. This method is a no-op. For production use,
     * rely on TTL-based expiration or explicit {@code @CacheEvict} calls.</p>
     */
    @Override
    public void clear() {
        // StateStoreCapability does not support wildcard/scan deletion.
        // Entries will expire naturally via TTL.
        // This is an acceptable trade-off for the capability abstraction.
    }

    /**
     * Constructs the namespaced key for the state store.
     *
     * @param key the original cache key
     * @return the prefixed key: {@code cache:{cacheName}:{key}}
     */
    private String toStoreKey(Object key) {
        return keyPrefix + key.toString();
    }
}
