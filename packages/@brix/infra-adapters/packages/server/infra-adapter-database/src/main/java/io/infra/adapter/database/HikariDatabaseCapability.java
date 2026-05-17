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
package io.infra.adapter.database;

import java.util.Objects;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

import io.runtime.sdk.capability.DatabaseCapability;
import io.runtime.sdk.capability.DatabaseDialect;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * HikariCP-based database capability implementation.
 * 
 * <p>This class is the standard adapter-side implementation of {@link DatabaseCapability}.
 * The public contract exposes only database metadata. The raw {@link DataSource} and
 * native SQL helpers remain adapter-internal and must not be consumed by plugins.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>Configuration-driven</b>: Database type, URL, credentials provided by external configuration</li>
 *   <li><b>Multi-vendor support</b>: Switch between PostgreSQL / Kingbase / MySQL via {@link DatabaseDialect}</li>
 *   <li><b>Connection pool management</b>: HikariCP high-performance pool with validation, timeout, and size config</li>
 *   <li><b>Schema isolation</b>: Supports Schema isolation for multi-tenancy scenarios</li>
 * </ul>
 * 
 * <h3>Architecture Compliance</h3>
 * <p>HikariCP-based DatabaseCapability implementation. Capability level: STANDARD.
 * Follows existing adapter patterns: {@code infra-adapter-kafka} for EventBusCapability,
 * {@code infra-adapter-redis} for StateStoreCapability.</p>
 * 
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. HikariDataSource and JdbcTemplate both support concurrent access.</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see DatabaseCapability
 * @see DatabaseDialect
 */
@Capability(
    type = DatabaseCapability.class,
    name = "hikari-database",
    description = "HikariCP-based database capability implementation with PostgreSQL/Kingbase multi-vendor support",
    level = CapabilityLevel.STANDARD,
    aliases = {"database", "databaseCapability"}
)
public class HikariDatabaseCapability implements DatabaseCapability {

    private static final Logger log = LoggerFactory.getLogger(HikariDatabaseCapability.class);

    /**
     * HikariCP DataSource.
     * 
     * <p>Created by auto-configuration based on external configuration,
     * encapsulates connection pool management logic.</p>
     */
    private final HikariDataSource dataSource;

    /**
     * Spring JDBC Template.
     * 
     * <p>Used to execute native SQL queries, encapsulates JDBC operation template methods.</p>
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Current database dialect.
     * 
     * <p>Determined by configuration, used by plugins to determine current database type.</p>
     */
    private final DatabaseDialect dialect;

    /**
     * Database name.
     */
    private final String databaseName;

    /**
     * Schema name.
     */
    private final String schemaName;

    /**
     * Constructor.
     * 
     * @param dataSource   HikariCP data source (created by auto-configuration)
     * @param dialect      Database dialect
     * @param databaseName Database name
     * @param schemaName   Schema name
     */
    public HikariDatabaseCapability(
            HikariDataSource dataSource,
            DatabaseDialect dialect,
            String databaseName,
            String schemaName) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName cannot be null");
        this.schemaName = schemaName != null ? schemaName : "public";
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        log.info("[Database] Database capability initialized: dialect={}, database={}, schema={}",
                dialect.getDisplayName(), databaseName, this.schemaName);
    }

    /**
     * Returns the adapter-managed data source for infrastructure assembly code.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DatabaseDialect getDialect() {
        return dialect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSchemaName() {
        return schemaName;
    }

    /**
     * Executes native SQL for adapter/platform-internal infrastructure code.
     * 
     * @param sql        Native SQL statement
     * @param resultType Result type
     * @param params     SQL parameters
     * @param <T>        Result type
     * @return Query result
     */
    public <T> T executeNative(String sql, Class<T> resultType, Object... params) {
        Objects.requireNonNull(sql, "sql cannot be null");
        Objects.requireNonNull(resultType, "resultType cannot be null");

        log.debug("[Database] Executing native SQL: sql={}, dialect={}", sql, dialect.getDisplayName());
        return jdbcTemplate.queryForObject(sql, resultType, params);
    }
}
