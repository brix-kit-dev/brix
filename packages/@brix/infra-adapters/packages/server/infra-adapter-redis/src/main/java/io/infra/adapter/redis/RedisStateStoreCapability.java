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

import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based state store capability implementation.
 * 
 * <p>This class implements {@link StateStoreCapability} Full Product Host implementation,
 * providing Redis-based key-value storage capability. Modules use this implementation
 * to store and retrieve state data without being aware of Redis.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>JSON Serialization</b>: Uses Jackson for value serialization/deserialization</li>
 *   <li><b>TTL Support</b>: Supports automatic expiration</li>
 *   <li><b>Namespace Isolation</b>: Automatically adds prefix to avoid key conflicts</li>
 *   <li><b>Type Safety</b>: Generic methods ensure type safety</li>
 * </ul>
 * 
 * <h3>Key Naming Convention</h3>
 * <p>Final storage key format: {prefix}:{userKey}</p>
 * <p>Example: brix:state:booking:session:user123</p>
 * 
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe and can be used concurrently by multiple threads.</p>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 * @see StateStoreCapability
 */
@Capability(
    type = StateStoreCapability.class,
    name = "redis-state-store",
    description = "Redis-based state store capability implementation",
    level = CapabilityLevel.CORE,
    aliases = {"stateStore", "redisStateStore"}
)
public class RedisStateStoreCapability implements StateStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(RedisStateStoreCapability.class);

    /**
     * Default key prefix.
     */
    private static final String DEFAULT_PREFIX = "brix:state:";

    /**
     * Redis string template.
     * 
     * <p>Uses StringRedisTemplate to ensure serialization consistency.</p>
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * JSON serializer.
     */
    private final ObjectMapper objectMapper;

    /**
     * Key prefix.
     */
    private final String keyPrefix;

    /**
     * Constructor (uses default prefix).
     * 
     * @param redisTemplate Redis template
     * @param objectMapper  JSON serializer
     */
    public RedisStateStoreCapability(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, DEFAULT_PREFIX);
    }

    /**
     * Constructor (with specified prefix).
     * 
     * @param redisTemplate Redis template
     * @param objectMapper  JSON serializer
     * @param keyPrefix     key prefix
     */
    public RedisStateStoreCapability(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            String keyPrefix) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.keyPrefix = keyPrefix != null ? keyPrefix : DEFAULT_PREFIX;
    }

    /**
     * Gets the stored value.
     * 
     * <p>Reads JSON string from Redis and deserializes to the specified type.</p>
     * 
     * @param key  storage key, cannot be null
     * @param type value type, used for deserialization
     * @param <T>  value class name
     * @return stored value, returns {@link Optional#empty()} if not exists
     * @throws IllegalArgumentException if key or type is null
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        // Parameter validation
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        // Build full key
        String fullKey = buildKey(key);

        try {
            // Read from Redis
            String json = redisTemplate.opsForValue().get(fullKey);
            
            if (json == null) {
                log.debug("State store read: key={}, result=not exists", key);
                return Optional.empty();
            }

            // Deserialize
            T value = objectMapper.readValue(json, type);
            log.debug("State store read: key={}, type={}", key, type.getSimpleName());
            
            return Optional.of(value);
            
        } catch (IOException e) {
            throw new StateDeserializationException(key, type.getName(),
                    "State store deserialization failed: key=" + key + ", type=" + type.getName(), e);
        }
    }

    /**
     * Stores value (without expiration time).
     * 
     * <p>Note: Data without expiration time will persist forever, use with caution.</p>
     * 
     * @param key   storage key, cannot be null
     * @param value value to store, cannot be null
     * @throws IllegalArgumentException if key or value is null
     */
    @Override
    public void put(String key, Object value) {
        // Parameter validation
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        // Build full key
        String fullKey = buildKey(key);

        try {
            // Serialize
            String json = objectMapper.writeValueAsString(value);
            
            // Write to Redis (without expiration time)
            redisTemplate.opsForValue().set(fullKey, json);
            
            log.debug("State store write: key={}, type={}", key, value.getClass().getSimpleName());
            
        } catch (JsonProcessingException e) {
            throw new StateStoreException("State store serialization failed: " + key, e);
        }
    }

    /**
     * Stores value (with expiration time).
     * 
     * @param key   storage key, cannot be null
     * @param value value to store, cannot be null
     * @param ttl   expiration time, cannot be null or negative
     * @throws IllegalArgumentException if parameters are invalid
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        // Parameter validation
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl cannot be negative");
        }

        // Build full key
        String fullKey = buildKey(key);

        try {
            // Serialize
            String json = objectMapper.writeValueAsString(value);
            
            // Write to Redis (with expiration time)
            redisTemplate.opsForValue().set(fullKey, json, ttl.toMillis(), TimeUnit.MILLISECONDS);
            
            log.debug("State store write: key={}, type={}, ttl={}s", 
                    key, value.getClass().getSimpleName(), ttl.toSeconds());
            
        } catch (JsonProcessingException e) {
            throw new StateStoreException("State store serialization failed: " + key, e);
        }
    }

    /**
     * Deletes stored value.
     * 
     * @param key storage key, cannot be null
     * @throws IllegalArgumentException if key is null
     */
    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        String fullKey = buildKey(key);
        Boolean deleted = redisTemplate.delete(fullKey);
        
        log.debug("State store delete: key={}, result={}", key, deleted);
    }

    /**
     * Checks if key exists.
     * 
     * @param key storage key, cannot be null
     * @return true if key exists
     * @throws IllegalArgumentException if key is null
     */
    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        String fullKey = buildKey(key);
        Boolean exists = redisTemplate.hasKey(fullKey);
        
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Gets and removes value (atomic operation).
     * 
     * <p>Uses Redis GETDEL command for atomicity.</p>
     * 
     * @param key  storage key, cannot be null
     * @param type value type
     * @param <T>  value class name
     * @return stored value, returns {@link Optional#empty()} if not exists
     */
    @Override
    public <T> Optional<T> getAndRemove(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        String fullKey = buildKey(key);

        try {
            // Execute atomic operation
            String json = redisTemplate.opsForValue().getAndDelete(fullKey);
            
            if (json == null) {
                return Optional.empty();
            }

            T value = objectMapper.readValue(json, type);
            log.debug("State store get and delete: key={}", key);
            
            return Optional.of(value);
            
        } catch (IOException e) {
            throw new StateDeserializationException(key, type.getName(),
                    "State store deserialization failed: key=" + key + ", type=" + type.getName(), e);
        }
    }

    /**
     * Sets expiration time.
     * 
     * @param key storage key
     * @param ttl expiration time
     * @return true if set successfully
     */
    public boolean expire(String key, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");

        String fullKey = buildKey(key);
        Boolean result = redisTemplate.expire(fullKey, ttl);
        
        return Boolean.TRUE.equals(result);
    }

    /**
     * Gets remaining expiration time.
     * 
     * @param key storage key
     * @return remaining time, returns null if key doesn't exist or no expiration set
     */
    public Duration getExpire(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        String fullKey = buildKey(key);
        Long seconds = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
        
        if (seconds == null || seconds < 0) {
            return null;
        }
        
        return Duration.ofSeconds(seconds);
    }

    /**
     * Builds full Redis key.
     * 
     * @param key user-provided key
     * @return full key
     */
    private String buildKey(String key) {
        return keyPrefix + key;
    }
}
