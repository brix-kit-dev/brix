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
package io.brix.platform.observability.health;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import io.brix.platform.observability.ObservabilityProperties;

/**
 * Health check auto-configuration.
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@AutoConfiguration
public class HealthCheckAutoConfiguration {

    /**
     * Redis health indicator.
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "observability.health.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisHealthIndicator redisHealthIndicator(
            RedisConnectionFactory connectionFactory,
            ObservabilityProperties properties) {
        return new RedisHealthIndicator(connectionFactory, 
                properties.getHealth().getRedis().getTimeoutMs());
    }
}
