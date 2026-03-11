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
 * Database capability auto-configuration.
 * 
 * <p>Spring Boot auto-configuration class responsible for initializing HikariCP data source and
 * DatabaseCapability bean. Automatically activates when HikariCP dependency is present in the
 * classpath and database connection information is configured.</p>
 * 
 * <h3>Activation Conditions</h3>
 * <ul>
 *   <li>{@link HikariDataSource} class exists in classpath</li>
 *   <li>Configuration property {@code brix.infra.database.enabled} is true (default)</li>
 * </ul>
 * 
 * <h3>Configuration Example</h3>
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
 * <h3>Architecture Notes</h3>
 * <p>This configuration class encapsulates database drivers in the adapter layer (Layer 2.5).
 * Plugins access the database through the {@link DatabaseCapability} contract
 * without directly depending on PostgreSQL/Kingbase drivers.</p>
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
     * JDBC driver class name mapping - infrastructure details are managed by the adapter layer and not exposed to the SDK contract layer.
     */
    private static final Map<DatabaseDialect, String> DRIVER_CLASS_NAMES = Map.of(
            DatabaseDialect.POSTGRESQL, "org.postgresql.Driver",
            DatabaseDialect.KINGBASE, "com.kingbase8.Driver",
            DatabaseDialect.MYSQL, "com.mysql.cj.jdbc.Driver",
            DatabaseDialect.ORACLE, "oracle.jdbc.OracleDriver"
    );

    /**
     * Creates HikariCP data source.
     * 
     * <p>Builds HikariCP connection pool based on configuration properties, setting driver class,
     * connection URL, credentials, and pool parameters. Driver class is automatically selected
     * based on {@link DatabaseDialect}.</p>
     * 
     * @param properties Database configuration properties
     * @return HikariCP data source
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(HikariDataSource.class)
    @ConditionalOnProperty(name = "brix.infra.database.url")
    public HikariDataSource brixHikariDataSource(DatabaseProperties properties) {
        // Parse database dialect
        DatabaseDialect dialect = DatabaseDialect.fromName(properties.getDialect());

        // Build HikariCP configuration
        HikariConfig config = new HikariConfig();
        config.setPoolName("brix-database-pool");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(DRIVER_CLASS_NAMES.getOrDefault(dialect, "org.postgresql.Driver"));

        // Connection pool parameters
        DatabaseProperties.HikariProperties hikariProps = properties.getHikari();
        config.setMaximumPoolSize(hikariProps.getMaximumPoolSize());
        config.setMinimumIdle(hikariProps.getMinimumIdle());
        config.setConnectionTimeout(hikariProps.getConnectionTimeout());
        config.setIdleTimeout(hikariProps.getIdleTimeout());
        config.setMaxLifetime(hikariProps.getMaxLifetime());
        config.setConnectionTestQuery(hikariProps.getConnectionTestQuery());

        // Schema configuration
        if (properties.getSchemaName() != null && !"public".equals(properties.getSchemaName())) {
            config.setSchema(properties.getSchemaName());
        }

        log.info("[Database] Creating HikariCP data source: dialect={}, url={}, poolSize={}-{}", 
                dialect.getDisplayName(), properties.getUrl(),
                hikariProps.getMinimumIdle(), hikariProps.getMaximumPoolSize());

        return new HikariDataSource(config);
    }

    /**
     * Creates DatabaseCapability instance.
     * 
     * <p>Wraps HikariCP data source as a {@link DatabaseCapability} capability instance
     * for plugins to obtain and use via RuntimeContext.</p>
     * 
     * @param dataSource  HikariCP data source
     * @param properties  Database configuration properties
     * @return DatabaseCapability instance
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
     * @param dataSource HikariCP data source for health checks
     * @return DatabaseHealthIndicator instance
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseHealthIndicator.class)
    public DatabaseHealthIndicator databaseHealthIndicator(HikariDataSource dataSource) {
        return new DatabaseHealthIndicator(dataSource);
    }
}
