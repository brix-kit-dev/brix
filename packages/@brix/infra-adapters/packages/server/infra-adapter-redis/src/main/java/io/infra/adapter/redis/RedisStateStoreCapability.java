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
 * 基于 Redis 的状态存储能力实现
 * 
 * <p>本类实现{@link StateStoreCapability} Full Product Host 实现
 * 提供基于 Redis 的键值存储能力。模块通过此实现存取状态数据，
 * 无需感知 Redis 的存在。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>JSON 序列化。</b>：使用 Jackson 进行值的序列反序列化</li>
 *   <li><b>TTL 支持</b>：支持自动过期</li>
 *   <li><b>命名空间隔离</b>：自动添加前缀，避免键冲突</li>
 *   <li><b>类型安全</b>：泛型方法保证类型安全</li>
 * </ul>
 * 
 * <h3>键命名规。</h3>
 * <p>最终存储键格式：{prefix}:{userKey}</p>
 * <p>例如：shinwa:state:booking:session:user123</p>
 * 
 * <h3>线程安全</h3>
 * <p>本类是线程安全的，可以被多个线程并发使用。</p>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 * @see StateStoreCapability
 */
@Capability(
    type = StateStoreCapability.class,
    name = "redis-state-store",
    description = "基于 Redis 的状态存储能力实现",
    level = CapabilityLevel.CORE,
    aliases = {"stateStore", "redisStateStore"}
)
public class RedisStateStoreCapability implements StateStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(RedisStateStoreCapability.class);

    /**
     * 默认键前缀
     */
    private static final String DEFAULT_PREFIX = "shinwa:state:";

    /**
     * Redis 字符串模板
     * 
     * <p>使用 StringRedisTemplate 确保序列化一致。</p>
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper;

    /**
     * 键前缀
     */
    private final String keyPrefix;

    /**
     * 构造函数（使用默认前缀
     * 
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     */
    public RedisStateStoreCapability(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, DEFAULT_PREFIX);
    }

    /**
     * 构造函数（指定前缀
     * 
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     * @param keyPrefix     键前缀
     */
    public RedisStateStoreCapability(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            String keyPrefix) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.keyPrefix = keyPrefix != null ? keyPrefix : DEFAULT_PREFIX;
    }

    /**
     * 获取存储的
     * 
     * <p>Redis 读取 JSON 字符串并反序列化为指定类型</p>
     * 
     * @param key  存储键，不能为空
     * @param type 值的类型，用于反序列
     * @param <T>  值类名
     * @return 存储的值，如果不存在返回{@link Optional#empty()}
     * @throws IllegalArgumentException 如果 key type null
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        // 参数校验
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(type, "type 不能为空");

        // 构建完整
        String fullKey = buildKey(key);

        try {
            // Redis 读取
            String json = redisTemplate.opsForValue().get(fullKey);
            
            if (json == null) {
                log.debug("状态存储读取: key={}, 结果=不存在", key);
                return Optional.empty();
            }

            // 反序列化
            T value = objectMapper.readValue(json, type);
            log.debug("状态存储读 key={}, 类型={}", key, type.getSimpleName());
            
            return Optional.of(value);
            
        } catch (IOException e) {
            throw new StateDeserializationException(key, type.getName(),
                    "状态存储反序列化失败: key=" + key + ", type=" + type.getName(), e);
        }
    }

    /**
     * 存储值（无过期时间）
     * 
     * <p>注意：无过期时间的数据会一直存在，请谨慎使用。</p>
     * 
     * @param key   存储键，不能为空
     * @param value 要存储的值，不能为 null
     * @throws IllegalArgumentException 如果 key value null
     */
    @Override
    public void put(String key, Object value) {
        // 参数校验
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(value, "value 不能为空");

        // 构建完整
        String fullKey = buildKey(key);

        try {
            // 序列
            String json = objectMapper.writeValueAsString(value);
            
            // 写入 Redis（无过期时间
            redisTemplate.opsForValue().set(fullKey, json);
            
            log.debug("状态存储写 key={}, 类型={}", key, value.getClass().getSimpleName());
            
        } catch (JsonProcessingException e) {
            throw new StateStoreException("状态存储序列化失败: " + key, e);
        }
    }

    /**
     * 存储值（带过期时间）
     * 
     * @param key   存储键，不能为空
     * @param value 要存储的值，不能为 null
     * @param ttl   过期时间，不能为 null 或负
     * @throws IllegalArgumentException 如果参数无效
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        // 参数校验
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(value, "value 不能为空");
        Objects.requireNonNull(ttl, "ttl 不能为空");
        
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 不能为负数");
        }

        // 构建完整
        String fullKey = buildKey(key);

        try {
            // 序列
            String json = objectMapper.writeValueAsString(value);
            
            // 写入 Redis（带过期时间
            redisTemplate.opsForValue().set(fullKey, json, ttl.toMillis(), TimeUnit.MILLISECONDS);
            
            log.debug("状态存储写入: key={}, 类型={}, ttl={}秒", 
                    key, value.getClass().getSimpleName(), ttl.toSeconds());
            
        } catch (JsonProcessingException e) {
            throw new StateStoreException("状态存储序列化失败: " + key, e);
        }
    }

    /**
     * 删除存储的
     * 
     * @param key 存储键，不能为空
     * @throws IllegalArgumentException 如果 key null
     */
    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key 不能为空");

        String fullKey = buildKey(key);
        Boolean deleted = redisTemplate.delete(fullKey);
        
        log.debug("状态存储删 key={}, 结果={}", key, deleted);
    }

    /**
     * 检查键是否存在
     * 
     * @param key 存储键，不能为空
     * @return 如果键存在返回 true
     * @throws IllegalArgumentException 如果 key null
     */
    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key 不能为空");

        String fullKey = buildKey(key);
        Boolean exists = redisTemplate.hasKey(fullKey);
        
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 获取并删除值（原子操作
     * 
     * <p>使用 Redis GETDEL 命令实现原子。</p>
     * 
     * @param key  存储键，不能为空
     * @param type 值的类型
     * @param <T>  值类名
     * @return 存储的值，如果不存在返回{@link Optional#empty()}
     */
    @Override
    public <T> Optional<T> getAndRemove(String key, Class<T> type) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(type, "type 不能为空");

        String fullKey = buildKey(key);

        try {
            // 使用 execute 执行原子操作
            String json = redisTemplate.opsForValue().getAndDelete(fullKey);
            
            if (json == null) {
                return Optional.empty();
            }

            T value = objectMapper.readValue(json, type);
            log.debug("状态存储获取并删除: key={}", key);
            
            return Optional.of(value);
            
        } catch (IOException e) {
            throw new StateDeserializationException(key, type.getName(),
                    "状态存储反序列化失败: key=" + key + ", type=" + type.getName(), e);
        }
    }

    /**
     * 设置过期时间
     * 
     * @param key 存储
     * @param ttl 过期时间
     * @return 如果设置成功返回 true
     */
    public boolean expire(String key, Duration ttl) {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(ttl, "ttl 不能为空");

        String fullKey = buildKey(key);
        Boolean result = redisTemplate.expire(fullKey, ttl);
        
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取剩余过期时间
     * 
     * @param key 存储
     * @return 剩余时间，如果键不存在或没有设置过期时间返回 null
     */
    public Duration getExpire(String key) {
        Objects.requireNonNull(key, "key 不能为空");

        String fullKey = buildKey(key);
        Long seconds = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
        
        if (seconds == null || seconds < 0) {
            return null;
        }
        
        return Duration.ofSeconds(seconds);
    }

    /**
     * 构建完整Redis 
     * 
     * @param key 用户提供的键
     * @return 完整
     */
    private String buildKey(String key) {
        return keyPrefix + key;
    }
}
