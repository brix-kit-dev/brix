/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.time.Duration;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * State Store Capability Contract
 * 
 * <p>Provides an abstract interface for key-value storage, used for caching, sessions, and temporary data storage.
 * Modules operate on state data through this interface without knowing the underlying implementation (Redis/Memcached/Local Memory).</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Key-Value Storage: Supports storing and retrieving values of any type</li>
 *   <li>Expiration Policy: Supports TTL-based automatic expiration</li>
 *   <li>Existence Check: Efficiently determine if a key exists</li>
 * </ul>
 * 
 * <h3>Use Cases</h3>
 * <ul>
 *   <li>Caching hot data</li>
 *   <li>Storing user session information</li>
 *   <li>Temporary state data (e.g., verification codes)</li>
 *   <li>Cross-request context passing</li>
 * </ul>
 * 
 * <h3>Key Naming Convention</h3>
 * <p>Recommended colon-separated namespaces: {module}:{type}:{identifier}</p>
 * <pre>{@code
 * // Examples
 * "booking:session:user123"
 * "identity:captcha:phone-13800138000"
 * "contract:cache:contract-456"
 * }</pre>
 * 
 * <h3>Serialization Notes</h3>
 * <p>Value objects will be serialized to JSON for storage. Ensure:</p>
 * <ul>
 *   <li>Value objects have no-arg constructor (or Jackson-compatible construction)</li>
 *   <li>Fields have getter/setter or use Jackson annotations</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private StateStoreCapability stateStore;
 * 
 * public void cacheUser(User user) {
 *     // Cache user info, expires in 30 minutes
 *     stateStore.put("user:cache:" + user.getId(), user, Duration.ofMinutes(30));
 * }
 * 
 * public Optional<User> getUser(String userId) {
 *     return stateStore.get("user:cache:" + userId, User.class);
 * }
 * }</pre>
 * 
 * <h3>Implementation Notes</h3>
 * <p>This interface is implemented by the Host layer:</p>
 * <ul>
 *   <li>Full Product Host: Redis implementation</li>
 *   <li>Embedded Host: Local ConcurrentHashMap or customer system-provided cache</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Since("3.0.0")
public interface StateStoreCapability {

    /**
     * Get stored value
     * 
     * @param key  storage key, cannot be empty
     * @param type value type for deserialization
     * @param <T>  value type
     * @return stored value, or {@link Optional#empty()} if not exists
     * @throws IllegalArgumentException if key or type is null
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Store value (no expiration)
     * 
     * <p>Note: Data without expiration persists indefinitely. Use with caution to avoid memory leaks</p>
     * 
     * @param key   storage key, cannot be empty
     * @param value value to store, cannot be null
     * @throws IllegalArgumentException if key or value is null
     */
    void put(String key, Object value);

    /**
     * Store value (with expiration)
     * 
     * <p>Recommended to use this method with explicit expiration time</p>
     * 
     * @param key   storage key, cannot be empty
     * @param value value to store, cannot be null
     * @param ttl   time to live, cannot be null or negative
     * @throws IllegalArgumentException if parameters are invalid
     */
    void put(String key, Object value, Duration ttl);

    /**
     * Remove stored value
     * 
     * <p>This method does not throw exception if key does not exist</p>
     * 
     * @param key storage key, cannot be empty
     * @throws IllegalArgumentException if key is null
     */
    void remove(String key);

    /**
     * Check if key exists
     * 
     * <p>This method is more efficient than get() as it only checks existence without deserializing</p>
     * 
     * @param key storage key, cannot be empty
     * @return true if key exists, false otherwise
     * @throws IllegalArgumentException if key is null
     */
    boolean exists(String key);

    /**
     * Get and remove value (atomic operation)
     * 
     * <p>Commonly used for one-time verification codes, tokens, etc.</p>
     * 
     * @param key  storage key, cannot be empty
     * @param type value type
     * @param <T>  value type
     * @return stored value, or {@link Optional#empty()} if not exists
     */
    default <T> Optional<T> getAndRemove(String key, Class<T> type) {
        Optional<T> value = get(key, type);
        value.ifPresent(v -> remove(key));
        return value;
    }

    /**
     * Put if absent (atomic operation)
     * 
     * <p>Used for implementing simple distributed locks or duplicate submission prevention</p>
     * 
     * @param key   storage key
     * @param value value to store
     * @param ttl   time to live
     * @return true if successfully stored, false if key already exists
     */
    default boolean putIfAbsent(String key, Object value, Duration ttl) {
        if (!exists(key)) {
            put(key, value, ttl);
            return true;
        }
        return false;
    }
}
