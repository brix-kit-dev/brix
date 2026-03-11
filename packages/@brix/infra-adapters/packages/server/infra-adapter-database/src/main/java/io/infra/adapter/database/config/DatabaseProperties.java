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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Database capability configuration properties.
 * 
 * <p>Defines database capability configuration items corresponding to application.yml settings.
 * Supports configuration-driven multi-database vendor switching.</p>
 * 
 * <h3>Configuration Example</h3>
 * <pre>{@code
 * brix:
 *   infra:
 *     database:
 *       enabled: true
 *       dialect: postgresql          # postgresql / kingbase / mysql / oracle
 *       url: jdbc:postgresql://localhost:5432/brix
 *       username: postgres
 *       password: secret
 *       database-name: brix
 *       schema-name: public
 *       hikari:
 *         maximum-pool-size: 20
 *         minimum-idle: 5
 *         connection-timeout: 30000
 *         idle-timeout: 600000
 *         max-lifetime: 1800000
 * }</pre>
 * 
 * <h3>Architecture Compliance</h3>
 * <p>Configuration properties for database capability.</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.database")
public class DatabaseProperties {

    /**
     * Whether database capability is enabled.
     */
    private boolean enabled = true;

    /**
     * Database dialect name.
     * 
     * <p>Supported: postgresql, kingbase, mysql, oracle</p>
     */
    private String dialect = "postgresql";

    /**
     * JDBC connection URL.
     */
    private String url;

    /**
     * Database username.
     */
    private String username;

    /**
     * Database password.
     */
    private String password;

    /**
     * Database name.
     */
    private String databaseName = "brix";

    /**
     * Schema name (for multi-tenancy isolation).
     */
    private String schemaName = "public";

    /**
     * HikariCP connection pool configuration.
     */
    private HikariProperties hikari = new HikariProperties();

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public HikariProperties getHikari() {
        return hikari;
    }

    public void setHikari(HikariProperties hikari) {
        this.hikari = hikari;
    }

    // ==================== Nested Configuration Classes ====================

    /**
     * HikariCP connection pool configuration.
     * 
     * <p>Controls core connection pool parameters that affect database connection performance and resource usage.</p>
     */
    public static class HikariProperties {

        /**
         * Maximum connection pool size.
         */
        private int maximumPoolSize = 20;

        /**
         * Minimum idle connections.
         */
        private int minimumIdle = 5;

        /**
         * Connection timeout in milliseconds.
         */
        private long connectionTimeout = 30000;

        /**
         * Idle connection timeout in milliseconds.
         */
        private long idleTimeout = 600000;

        /**
         * Maximum connection lifetime in milliseconds.
         */
        private long maxLifetime = 1800000;

        /**
         * Connection validation SQL.
         */
        private String connectionTestQuery = "SELECT 1";

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public String getConnectionTestQuery() {
            return connectionTestQuery;
        }

        public void setConnectionTestQuery(String connectionTestQuery) {
            this.connectionTestQuery = connectionTestQuery;
        }
    }
}
