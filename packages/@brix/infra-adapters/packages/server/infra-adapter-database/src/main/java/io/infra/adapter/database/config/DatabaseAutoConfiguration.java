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
package io.infra.adapter.database.config;

import io.infra.adapter.database.HikariDatabaseCapability;
import io.infra.adapter.database.health.DatabaseHealthIndicator;
import io.runtime.sdk.capability.DatabaseCapability;
import io.runtime.sdk.capability.DatabaseDialect;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 数据库能力自动配置
 * 
 * <p>Spring Boot 自动配置类，负责初始化 HikariCP 数据源和 DatabaseCapability Bean。
 * 当 classpath 中存在 HikariCP 依赖且配置了数据库连接信息时自动生效。</p>
 * 
 * <h3>激活条件</h3>
 * <ul>
 *   <li>classpath 中存在 {@link HikariDataSource} 类</li>
 *   <li>配置项 {@code brix.infra.database.enabled} 为 true（默认）</li>
 * </ul>
 * 
 * <h3>配置示例</h3>
 * <pre>{@code
 * brix:
 *   infra:
 *     database:
 *       dialect: postgresql
 *       url: jdbc:postgresql://localhost:5432/shinwa
 *       username: postgres
 *       password: secret
 * }</pre>
 * 
 * <h3>架构说明</h3>
 * <p>本配置类将数据库驱动封装在适配器层（Layer 2.5），
 * 插件通过 {@link DatabaseCapability} 契约访问数据库，
 * 不直接依赖 PostgreSQL / Kingbase 等驱动。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see DatabaseCapability
 * @see HikariDatabaseCapability
 */
@AutoConfiguration
@ConditionalOnClass(HikariDataSource.class)
@ConditionalOnProperty(name = "brix.infra.database.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAutoConfiguration.class);

    /**
     * JDBC 驱动类名映射 — 基础设施细节由适配器层管理，不暴露到 SDK 契约层
     */
    private static final Map<DatabaseDialect, String> DRIVER_CLASS_NAMES = Map.of(
            DatabaseDialect.POSTGRESQL, "org.postgresql.Driver",
            DatabaseDialect.KINGBASE, "com.kingbase8.Driver",
            DatabaseDialect.MYSQL, "com.mysql.cj.jdbc.Driver",
            DatabaseDialect.ORACLE, "oracle.jdbc.OracleDriver"
    );

    /**
     * 创建 HikariCP 数据源
     * 
     * <p>根据配置属性构建 HikariCP 连接池，设置驱动类、连接 URL、凭证和连接池参数。
     * 驱动类根据 {@link DatabaseDialect} 自动选择。</p>
     * 
     * @param properties 数据库配置属性
     * @return HikariCP 数据源
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(HikariDataSource.class)
    @ConditionalOnProperty(name = "brix.infra.database.url")
    public HikariDataSource brixHikariDataSource(DatabaseProperties properties) {
        // 解析数据库方言
        DatabaseDialect dialect = DatabaseDialect.fromName(properties.getDialect());

        // 构建 HikariCP 配置
        HikariConfig config = new HikariConfig();
        config.setPoolName("brix-database-pool");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(DRIVER_CLASS_NAMES.getOrDefault(dialect, "org.postgresql.Driver"));

        // 连接池参数
        DatabaseProperties.HikariProperties hikariProps = properties.getHikari();
        config.setMaximumPoolSize(hikariProps.getMaximumPoolSize());
        config.setMinimumIdle(hikariProps.getMinimumIdle());
        config.setConnectionTimeout(hikariProps.getConnectionTimeout());
        config.setIdleTimeout(hikariProps.getIdleTimeout());
        config.setMaxLifetime(hikariProps.getMaxLifetime());
        config.setConnectionTestQuery(hikariProps.getConnectionTestQuery());

        // Schema 设置
        if (properties.getSchemaName() != null && !"public".equals(properties.getSchemaName())) {
            config.setSchema(properties.getSchemaName());
        }

        log.info("[Database] 创建 HikariCP 数据源: dialect={}, url={}, poolSize={}-{}", 
                dialect.getDisplayName(), properties.getUrl(),
                hikariProps.getMinimumIdle(), hikariProps.getMaximumPoolSize());

        return new HikariDataSource(config);
    }

    /**
     * 创建 DatabaseCapability 实例
     * 
     * <p>将 HikariCP 数据源封装为 {@link DatabaseCapability} 能力实例，
     * 供插件通过 RuntimeContext 获取和使用。</p>
     * 
     * @param dataSource  HikariCP 数据源
     * @param properties  数据库配置属性
     * @return DatabaseCapability 实例
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseCapability.class)
    public DatabaseCapability hikariDatabaseCapability(
            HikariDataSource dataSource,
            DatabaseProperties properties) {
        DatabaseDialect dialect = DatabaseDialect.fromName(properties.getDialect());
        return new HikariDatabaseCapability(
                dataSource,
                dialect,
                properties.getDatabaseName(),
                properties.getSchemaName()
        );
    }

    /**
     * Configures database health indicator for Actuator.
     *
     * <p>Reports database connectivity status at /actuator/health endpoint.
     * Includes HikariCP pool statistics for monitoring.</p>
     *
     * <p>配置数据库健康指示器，在 /actuator/health 端点报告连通性状态。
     * 包含 HikariCP 连接池统计信息用于监控。</p>
     *
     * @param dataSource HikariCP data source for health checks
     * @return DatabaseHealthIndicator instance
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseHealthIndicator.class)
    public DatabaseHealthIndicator databaseHealthIndicator(HikariDataSource dataSource) {
        return new DatabaseHealthIndicator(dataSource);
    }
}
