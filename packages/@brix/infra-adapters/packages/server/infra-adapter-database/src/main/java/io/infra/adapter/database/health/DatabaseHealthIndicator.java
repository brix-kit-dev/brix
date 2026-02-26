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
package io.infra.adapter.database.health;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/**
 * Health indicator for database connectivity via HikariCP.
 *
 * <p>Provides health status for Spring Boot Actuator at /actuator/health endpoint.
 * Reports UP when a database connection can be obtained and validated, DOWN otherwise.</p>
 *
 * <h3>Health Details</h3>
 * <ul>
 *   <li><b>database</b>: Database product name</li>
 *   <li><b>version</b>: Database product version</li>
 *   <li><b>pool.active</b>: Number of active connections</li>
 *   <li><b>pool.idle</b>: Number of idle connections</li>
 *   <li><b>pool.total</b>: Total connections in pool</li>
 *   <li><b>pool.waiting</b>: Threads waiting for connection</li>
 * </ul>
 *
 * <p>健康指示器用于检查通过 HikariCP 的数据库连通性。当可以获取并验证数据库连接时报告 UP，
 * 否则报告 DOWN。</p>
 *
 * @author Brix Platform Team
 * @since 3.0.0
 * @see HealthIndicator
 * @see HikariDataSource
 */
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    private final HikariDataSource dataSource;

    /**
     * Creates a new database health indicator.
     *
     * @param dataSource the HikariCP data source for health checks
     */
    public DatabaseHealthIndicator(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Performs health check by obtaining a connection and querying metadata.
     *
     * <p>The health check obtains a connection from the pool and retrieves database
     * metadata to verify connectivity. Pool statistics are also included for monitoring.</p>
     *
     * <p>通过从连接池获取连接并查询元数据来执行健康检查。连接池统计信息也包含在内用于监控。</p>
     *
     * @return Health status with database and pool details
     */
    @Override
    public Health health() {
        Health.Builder builder = Health.unknown();

        try (Connection connection = dataSource.getConnection()) {
            // 获取数据库元数据 - Get database metadata
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseName = metaData.getDatabaseProductName();
            String databaseVersion = metaData.getDatabaseProductVersion();

            // 获取连接池统计 - Get connection pool statistics
            HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();

            builder.up()
                    .withDetail("database", databaseName)
                    .withDetail("version", databaseVersion)
                    .withDetail("pool.name", dataSource.getPoolName());

            if (pool != null) {
                // 连接池详情 - Connection pool details
                builder.withDetail("pool.active", pool.getActiveConnections())
                        .withDetail("pool.idle", pool.getIdleConnections())
                        .withDetail("pool.total", pool.getTotalConnections())
                        .withDetail("pool.waiting", pool.getThreadsAwaitingConnection());

                // 警告：等待线程过多 - Warning: too many waiting threads
                if (pool.getThreadsAwaitingConnection() > 0) {
                    log.warn("Database health: {} threads waiting for connection", 
                            pool.getThreadsAwaitingConnection());
                }

                // 警告：连接池接近满载 - Warning: pool near capacity
                int activeRatio = pool.getActiveConnections() * 100 / dataSource.getMaximumPoolSize();
                if (activeRatio > 80) {
                    log.warn("Database health: connection pool at {}% capacity", activeRatio);
                }
            }

            log.debug("Database health check: UP - database={}, version={}", databaseName, databaseVersion);
            return builder.build();

        } catch (SQLException e) {
            // 连接失败 - Connection failed
            log.warn("Database health check failed: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("error", e.getMessage())
                    .withDetail("sqlState", e.getSQLState())
                    .withDetail("errorCode", e.getErrorCode())
                    .build();
        }
    }
}
