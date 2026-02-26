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
package io.infra.adapter.redis.health;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Health indicator for Redis connectivity.
 *
 * <p>Provides health status for Spring Boot Actuator at /actuator/health endpoint.
 * Reports UP when Redis server responds to PING command, DOWN otherwise.</p>
 *
 * <h3>Health Details</h3>
 * <ul>
 *   <li><b>version</b>: Redis server version</li>
 *   <li><b>mode</b>: Redis mode (standalone/cluster/sentinel)</li>
 *   <li><b>connectedClients</b>: Number of connected clients</li>
 *   <li><b>usedMemory</b>: Memory usage of Redis server</li>
 * </ul>
 *
 * <p>健康指示器用于检查 Redis 服务器的连通性。当 Redis 响应 PING 命令时报告 UP，
 * 否则报告 DOWN。</p>
 *
 * @author Brix Platform Team
 * @since 3.0.0
 * @see HealthIndicator
 * @see RedisConnectionFactory
 */
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthIndicator.class);

    private final RedisConnectionFactory connectionFactory;

    /**
     * Creates a new Redis health indicator.
     *
     * @param connectionFactory the Redis connection factory for health checks
     */
    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Performs health check by executing PING and INFO commands.
     *
     * <p>The health check connects to Redis and verifies connectivity via PING command.
     * Additional server information is retrieved via INFO command for diagnostic purposes.</p>
     *
     * <p>通过执行 PING 和 INFO 命令来执行健康检查。PING 命令验证连通性，
     * INFO 命令获取服务器诊断信息。</p>
     *
     * @return Health status with server details
     */
    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            // 执行 PING 命令验证连通性 - Execute PING command to verify connectivity
            String pingResponse = connection.ping();
            
            if (!"PONG".equals(pingResponse)) {
                log.warn("Redis health check: unexpected PING response: {}", pingResponse);
                return Health.down()
                        .withDetail("error", "Unexpected PING response: " + pingResponse)
                        .build();
            }

            // 获取服务器信息 - Get server info
            Properties serverInfo = connection.serverCommands().info("server");
            Properties clientInfo = connection.serverCommands().info("clients");
            Properties memoryInfo = connection.serverCommands().info("memory");

            String version = serverInfo != null ? serverInfo.getProperty("redis_version", "unknown") : "unknown";
            String mode = serverInfo != null ? serverInfo.getProperty("redis_mode", "standalone") : "standalone";
            String connectedClients = clientInfo != null ? clientInfo.getProperty("connected_clients", "N/A") : "N/A";
            String usedMemory = memoryInfo != null ? memoryInfo.getProperty("used_memory_human", "N/A") : "N/A";

            log.debug("Redis health check: UP - version={}, mode={}, clients={}", version, mode, connectedClients);

            return Health.up()
                    .withDetail("version", version)
                    .withDetail("mode", mode)
                    .withDetail("connectedClients", connectedClients)
                    .withDetail("usedMemory", usedMemory)
                    .build();

        } catch (Exception e) {
            // 连接失败 - Connection failed
            log.warn("Redis health check failed: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
