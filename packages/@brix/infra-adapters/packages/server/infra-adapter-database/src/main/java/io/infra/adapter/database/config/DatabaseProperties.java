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
 * 数据库能力配置属性
 * 
 * <p>定义数据库能力的配置项，对应 application.yml 中的配置。
 * 支持配置驱动的多数据库厂商切换。</p>
 * 
 * <h3>配置示例</h3>
 * <pre>{@code
 * brix:
 *   infra:
 *     database:
 *       enabled: true
 *       dialect: postgresql          # postgresql / kingbase / mysql / oracle
 *       url: jdbc:postgresql://localhost:5432/shinwa
 *       username: postgres
 *       password: secret
 *       database-name: shinwa
 *       schema-name: public
 *       hikari:
 *         maximum-pool-size: 20
 *         minimum-idle: 5
 *         connection-timeout: 30000
 *         idle-timeout: 600000
 *         max-lifetime: 1800000
 * }</pre>
 * 
 * <h3>蓝图对照</h3>
 * <p>对应蓝图 v3.0.2 第 3.3.1 节中的 YAML 配置方案。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.database")
public class DatabaseProperties {

    /**
     * 是否启用数据库能力
     */
    private boolean enabled = true;

    /**
     * 数据库方言名称
     * 
     * <p>支持：postgresql、kingbase、mysql、oracle</p>
     */
    private String dialect = "postgresql";

    /**
     * JDBC 连接 URL
     */
    private String url;

    /**
     * 数据库用户名
     */
    private String username;

    /**
     * 数据库密码
     */
    private String password;

    /**
     * 数据库名称
     */
    private String databaseName = "shinwa";

    /**
     * Schema 名称（用于多租户隔离）
     */
    private String schemaName = "public";

    /**
     * HikariCP 连接池配置
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

    // ==================== 嵌套配置类 ====================

    /**
     * HikariCP 连接池配置
     * 
     * <p>控制连接池的核心参数，影响数据库连接的性能和资源使用。</p>
     */
    public static class HikariProperties {

        /**
         * 最大连接池大小
         */
        private int maximumPoolSize = 20;

        /**
         * 最小空闲连接数
         */
        private int minimumIdle = 5;

        /**
         * 连接超时时间（毫秒）
         */
        private long connectionTimeout = 30000;

        /**
         * 空闲连接超时时间（毫秒）
         */
        private long idleTimeout = 600000;

        /**
         * 连接最大生命周期（毫秒）
         */
        private long maxLifetime = 1800000;

        /**
         * 连接验证 SQL
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
