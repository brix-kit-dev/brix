package io.brix.platform.observability.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis 健康指示
 * <p>
 * 检Redis 连接状态和响应时间
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(RedisHealthIndicator.class);

    private final RedisConnectionFactory connectionFactory;
    private final long timeoutMs;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory, long timeoutMs) {
        this.connectionFactory = connectionFactory;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Health health() {
        long startTime = System.currentTimeMillis();
        
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if ("PONG".equalsIgnoreCase(pong)) {
                Health.Builder builder = Health.up()
                        .withDetail("responseTime", responseTime + "ms");
                
                // 响应时间过长时添加警
                if (responseTime > timeoutMs / 2) {
                    builder.withDetail("warning", "Response time is high");
                }
                
                return builder.build();
            } else {
                return Health.down()
                        .withDetail("error", "Unexpected response: " + pong)
                        .build();
            }
        } catch (Exception e) {
            logger.warn("Redis health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
