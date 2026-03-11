/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import javax.sql.DataSource;

import io.runtime.sdk.annotation.InternalApi;

/**
 * Database Capability Contract
 * 
 * <p>Provides an abstract interface for database access, allowing plugins to operate without
 * knowledge of the underlying database type (PostgreSQL / Kingbase / MySQL / Oracle),
 * and switch database vendors through configuration.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Provide DataSource: Plugins obtain the data source through this interface without managing connection pool configuration</li>
 *   <li>Database dialect abstraction: Plugins can get the current dialect to handle SQL syntax differences</li>
 *   <li>Database/Schema management: Supports Schema isolation in multi-tenant scenarios</li>
 *   <li>Native SQL execution: Provides controlled native query capability</li>
 * </ul>
 * 
 * <h3>Design Constraints</h3>
 * <ul>
 *   <li><b>Plugin transparent</b>: No database driver dependencies (postgresql, kingbase, etc.) in plugin code</li>
 *   <li><b>Configuration-driven</b>: Database type and connection info managed by Host layer configuration</li>
 *   <li><b>Unified connection pool</b>: Connection pool (HikariCP) is managed uniformly by adapters</li>
 * </ul>
 * 
 * <h3>Architecture Compliance</h3>
 * <p>Provides database access abstraction for plugins.
 * Capability level: STANDARD, recommended for all Host implementations.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Plugin obtains database capability through RuntimeContext
 * DatabaseCapability db = context.getDatabase();
 * 
 * // Get data source for JPA/JDBC
 * DataSource dataSource = db.getDataSource();
 * 
 * // Get current dialect (for SQL differences)
 * DatabaseDialect dialect = db.getDialect();
 * 
 * // Execute native SQL
 * Long count = db.executeNative("SELECT COUNT(*) FROM users", Long.class);
 * }</pre>
 * 
 * <h3>Implementation Notes</h3>
 * <p>This interface is implemented by the infrastructure adapter layer (Layer 2.5):</p>
 * <ul>
 *   <li>{@code infra-adapter-database}: Encapsulates PostgreSQL / Kingbase / MySQL drivers</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see DatabaseDialect
 */
public interface DatabaseCapability {

    /**
     * Gets the data source
     * 
     * <p>Returns the currently configured data source instance. Connection pool management
     * is handled by the adapter. Plugins can inject this data source into JPA EntityManagerFactory
     * or use it directly for JDBC operations.</p>
     * 
     * <h4>Internal API Notice</h4>
     * <p>This method is marked as {@code @InternalApi} as it exposes the infrastructure type {@link DataSource}.
     * Business plugins should prefer the following approaches:</p>
     * <ul>
     *   <li><b>JPA approach</b>: Perform data operations through dependency-injected EntityManager</li>
     *   <li><b>Native SQL</b>: Use the {@link #executeNative(String, Class, Object...)} method</li>
     * </ul>
     * <p>This method is retained for Host adapter layer and framework extensions.</p>
     * 
     * @return the data source instance, never returns null
     */
    @InternalApi(value = "Exposes infrastructure type DataSource, business plugins should use JPA or executeNative()", 
                 instead = "executeNative")
    DataSource getDataSource();

    /**
     * Gets the current database dialect
     * 
     * <p>Used to handle SQL syntax differences across databases. In most scenarios,
     * plugins using JPA don't need to care about the dialect. Query the dialect only
     * when writing native SQL or using database-specific features.</p>
     * 
     * @return the current database dialect enum
     * @see DatabaseDialect
     */
    DatabaseDialect getDialect();

    /**
     * Gets the database name
     * 
     * <p>Returns the currently connected database name, managed by Host layer configuration.</p>
     * 
     * @return the database name
     */
    String getDatabaseName();

    /**
     * Gets the Schema name
     * 
     * <p>Supports Schema isolation in multi-tenant scenarios, returns the Schema name for the current tenant.</p>
     * 
     * @return the Schema name
     */
    String getSchemaName();

    /**
     * Executes a native SQL query
     * 
     * <p>Provides controlled native SQL execution capability for complex queries or
     * database-specific operations. Prefer JPA and use this method only when JPA cannot meet requirements.</p>
     * 
     * <h4>Security Notice</h4>
     * <p>SQL parameters are passed through placeholders. Concatenating SQL strings is prohibited to prevent SQL injection.</p>
     * 
     * @param sql        the native SQL statement, using {@code ?} as parameter placeholders
     * @param resultType the result type
     * @param params     SQL parameters (matched to placeholders in order)
     * @param <T>        the result type
     * @return the query result
     * @throws IllegalArgumentException if SQL is null or resultType is null
     */
    <T> T executeNative(String sql, Class<T> resultType, Object... params);
}
