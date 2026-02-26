package io.brix.platform.observability.health;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import io.brix.platform.observability.ObservabilityProperties;

/**
 * 健康检查自动配
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@AutoConfiguration
public class HealthCheckAutoConfiguration {

    /**
     * Redis 健康指示
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
