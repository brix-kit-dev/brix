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
package io.infra.adapter.redis.config;

import io.infra.adapter.redis.RedisLockCapability;
import io.infra.adapter.redis.RedisStateStoreCapability;
import io.infra.adapter.redis.health.RedisHealthIndicator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 能力自动配置
 * 
 * <p>Spring Boot 自动配置类，负责初始化 Redis 相关Bean
 * classpath 中存Redis 依赖且配置了 Redis 连接信息时自动生效。</p>
 * 
 * <h3>配置。</h3>
 * <table border="1">
 *   <tr><th>配置</th><th>说明</th><th>默认</th></tr>
 *   <tr><td>shinwa.runtime.redis.enabled</td><td>是否启用</td><td>true</td></tr>
 *   <tr><td>shinwa.runtime.redis.key-prefix</td><td>键前缀</td><td>shinwa:state:</td></tr>
 * </table>
 * 
 * <h3>提供的能力</h3>
 * <ul>
 *   <li>{@link StateStoreCapability} - 状态存储能力</li>
 *   <li>{@link LockCapability} - 分布式锁能力</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(name = "brix.infra.redis.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisCapabilityProperties.class)
public class RedisAutoConfiguration {

    /**
     * 配置 StringRedisTemplate
     * 
     * @param connectionFactory Redis 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 创建状态存储专用 ObjectMapper（内部使用，不注册为 Spring Bean）
     * 
     * <p>避免与 Spring Boot 自动配置的全局 ObjectMapper 冲突。</p>
     * 
     * @return ObjectMapper 实例
     */
    private static ObjectMapper createStateStoreObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册 Java 8 时间模块
        mapper.registerModule(new JavaTimeModule());
        
        // 日期时间序列化为 ISO-8601 格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性（兼容性）
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
                false);
        
        return mapper;
    }

    /**
     * 配置 StateStoreCapability 实现
     * 
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     * @param properties    配置属性
     * @return StateStoreCapability 实例
     */
    @Bean
    @ConditionalOnMissingBean(StateStoreCapability.class)
    public StateStoreCapability redisStateStoreCapability(
            StringRedisTemplate redisTemplate,
            RedisCapabilityProperties properties) {
        return new RedisStateStoreCapability(redisTemplate, createStateStoreObjectMapper(), properties.getKeyPrefix());
    }

    /**
     * 配置 LockCapability 实现
     * 
     * @param redisTemplate Redis 模板
     * @param properties    配置属性
     * @return LockCapability 实例
     */
    @Bean
    @ConditionalOnMissingBean(LockCapability.class)
    public LockCapability redisLockCapability(StringRedisTemplate redisTemplate, RedisCapabilityProperties properties) {
        RedisCapabilityProperties.LockProperties lockProps = properties.getLock();
        return new RedisLockCapability(redisTemplate, lockProps.getKeyPrefix(), lockProps.getDefaultExpireSeconds());
    }

    /**
     * Configures Redis health indicator for Actuator.
     *
     * <p>Reports Redis server connectivity status at /actuator/health endpoint.</p>
     *
     * <p>配置 Redis 健康指示器，在 /actuator/health 端点报告服务器连通性状态。</p>
     *
     * @param connectionFactory Redis connection factory for health checks
     * @return RedisHealthIndicator instance
     */
    @Bean
    @ConditionalOnMissingBean(RedisHealthIndicator.class)
    public RedisHealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return new RedisHealthIndicator(connectionFactory);
    }
}
