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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.infra.adapter.redis.RedisLockCapability;
import io.infra.adapter.redis.RedisStateStoreCapability;
import io.infra.adapter.redis.health.RedisHealthIndicator;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.StateStoreCapability;

/**
 * Redis capability auto-configuration.
 * 
 * <p>Spring Boot auto-configuration class responsible for initializing Redis related Beans.
 * Automatically takes effect when Redis dependencies exist in the classpath and Redis connection info is configured.</p>
 * 
 * <h3>Configuration.</h3>
 * <table border="1">
 *   <tr><th>Configuration</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>brix.runtime.redis.enabled</td><td>Whether to enable</td><td>true</td></tr>
 *   <tr><td>brix.runtime.redis.key-prefix</td><td>Key prefix</td><td>brix:state:</td></tr>
 * </table>
 * 
 * <h3>Provided Capabilities</h3>
 * <ul>
 *   <li>{@link StateStoreCapability} - State store capability</li>
 *   <li>{@link LockCapability} - Distributed lock capability</li>
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
     * Configures StringRedisTemplate.
     * 
     * @param connectionFactory Redis connection factory
     * @return StringRedisTemplate instance
     */
    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Creates ObjectMapper dedicated for state store (for internal use, not registered as Spring Bean).
     * 
     * <p>Avoids conflicts with the global ObjectMapper auto-configured by Spring Boot.</p>
     * 
     * @return ObjectMapper instance
     */
    private static ObjectMapper createStateStoreObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register Java 8 time module
        mapper.registerModule(new JavaTimeModule());
        
        // Serialize date/time in ISO-8601 format
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Ignore unknown properties (for compatibility)
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
                false);
        
        return mapper;
    }

    /**
     * Configures StateStoreCapability implementation.
     * 
     * @param redisTemplate Redis template
     * @param properties    configuration properties
     * @return StateStoreCapability instance
     */
    @Bean
    @ConditionalOnMissingBean(StateStoreCapability.class)
    public RedisStateStoreCapability redisStateStoreCapability(
            StringRedisTemplate redisTemplate,
            RedisCapabilityProperties properties) {
        return new RedisStateStoreCapability(redisTemplate, createStateStoreObjectMapper(), properties.getKeyPrefix());
    }

    /**
     * Configures LockCapability implementation.
     * 
     * @param redisTemplate Redis template
     * @param properties    configuration properties
     * @return LockCapability instance
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
     * @param connectionFactory Redis connection factory for health checks
     * @return RedisHealthIndicator instance
     */
    @Bean
    @ConditionalOnMissingBean(RedisHealthIndicator.class)
    public RedisHealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return new RedisHealthIndicator(connectionFactory);
    }
}
