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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis health indicator.
 * <p>
 * Checks Redis connection status and response time.
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
                
                // Add warning when response time is too long
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
