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

import io.runtime.sdk.annotation.Since;

/**
 * Database Capability Contract
 * 
 * <p>Provides database environment metadata without exposing JDBC or connection-pool
 * implementation types to plugins.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Database dialect abstraction: Plugins can get the current dialect to handle SQL syntax differences</li>
 *   <li>Database/Schema management: Supports Schema isolation in multi-tenant scenarios</li>
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
 * <p>The contract deliberately does not expose connection-pool handles, JDBC, JPA
 * infrastructure, or raw SQL execution APIs. Data access must be performed through
 * plugin-owned repositories or typed query capabilities that enforce tenant and
 * plugin ownership boundaries.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Plugin obtains database capability through RuntimeContext
 * // Get current dialect/profile metadata.
 * DatabaseDialect dialect = db.getDialect();
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
@Since("3.0.0")
public interface DatabaseCapability {

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
}
