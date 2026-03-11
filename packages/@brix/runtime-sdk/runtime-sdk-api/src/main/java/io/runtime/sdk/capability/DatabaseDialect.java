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

/**
 * Database Dialect Enumeration
 * 
 * <p>Defines database vendor identifiers supported by the platform.
 * Used for configuration-driven multi-database vendor switching.</p>
 * 
 * <h3>Vendor Support</h3>
 * <table border="1">
 *   <tr><th>Vendor</th><th>Notes</th></tr>
 *   <tr><td>PostgreSQL</td><td>Primary recommendation</td></tr>
 *   <tr><td>Kingbase</td><td>Compatibility mode</td></tr>
 *   <tr><td>MySQL</td><td>Reserved</td></tr>
 *   <tr><td>Oracle</td><td>Reserved</td></tr>
 * </table>
 * 
 * <h3>Usage</h3>
 * <pre>{@code
 * DatabaseDialect dialect = databaseCapability.getDialect();
 * if (dialect == DatabaseDialect.KINGBASE) {
 *     // Handle Kingbase-specific SQL syntax
 * }
 * }</pre>
 * 
 * <p><b>Note</b>: Hibernate dialect class names and JDBC driver class names are infrastructure details,
 * mapped by the adapter layer (infra-adapter-database), not exposed in the contract layer.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see DatabaseCapability
 */
public enum DatabaseDialect {

    /**
     * PostgreSQL Database
     * 
     * <p>The primary recommended relational database for the platform.</p>
     */
    POSTGRESQL("PostgreSQL"),

    /**
     * Kingbase Database
     * 
     * <p>A domestic database using PostgreSQL compatibility mode.
     * Kingbase V8 is highly compatible with PostgreSQL at the SQL syntax level.</p>
     */
    KINGBASE("Kingbase"),

    /**
     * MySQL Database (reserved)
     */
    MYSQL("MySQL"),

    /**
     * Oracle Database (reserved)
     */
    ORACLE("Oracle");

    /** Database display name */
    private final String displayName;

    DatabaseDialect(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the database display name
     * 
     * @return the database display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Finds the dialect enum by name (case-insensitive)
     * 
     * @param name the dialect name
     * @return the matching dialect enum
     * @throws IllegalArgumentException if the name does not match any dialect
     */
    public static DatabaseDialect fromName(String name) {
        for (DatabaseDialect dialect : values()) {
            if (dialect.name().equalsIgnoreCase(name) 
                    || dialect.displayName.equalsIgnoreCase(name)) {
                return dialect;
            }
        }
        throw new IllegalArgumentException("Unsupported database dialect: " + name 
                + ", supported dialects: POSTGRESQL, KINGBASE, MYSQL, ORACLE");
    }
}
