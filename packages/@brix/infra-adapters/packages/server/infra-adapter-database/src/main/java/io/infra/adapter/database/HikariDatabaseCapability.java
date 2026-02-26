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

import io.runtime.sdk.capability.DatabaseCapability;
import io.runtime.sdk.capability.DatabaseDialect;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * 基于 HikariCP 的数据库能力实现
 * 
 * <p>本类是 {@link DatabaseCapability} 的标准实现，提供基于 HikariCP 连接池的数据库访问能力。
 * 插件通过此实现获取数据源、查询方言、执行原生 SQL，无需感知具体数据库驱动。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>配置驱动</b>：数据库类型、URL、凭证由外部配置提供</li>
 *   <li><b>多厂商支持</b>：通过 {@link DatabaseDialect} 切换 PostgreSQL / Kingbase / MySQL</li>
 *   <li><b>连接池管理</b>：HikariCP 高性能连接池，支持连接验证、超时、池大小配置</li>
 *   <li><b>Schema 隔离</b>：支持多租户场景下的 Schema 隔离</li>
 * </ul>
 * 
 * <h3>蓝图对照</h3>
 * <p>对应蓝图 v3.0.2 第 3.3.1 节，能力级别为 STANDARD。
 * 参考现有模式：{@code infra-adapter-kafka} 实现 EventBusCapability、
 * {@code infra-adapter-redis} 实现 StateStoreCapability。</p>
 * 
 * <h3>线程安全</h3>
 * <p>本类是线程安全的。HikariDataSource 和 JdbcTemplate 均支持并发访问。</p>
 * 
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see DatabaseCapability
 * @see DatabaseDialect
 */
@Capability(
    type = DatabaseCapability.class,
    name = "hikari-database",
    description = "基于 HikariCP 的数据库能力实现，支持 PostgreSQL / Kingbase 多厂商切换",
    level = CapabilityLevel.STANDARD,
    aliases = {"database", "databaseCapability"}
)
public class HikariDatabaseCapability implements DatabaseCapability {

    private static final Logger log = LoggerFactory.getLogger(HikariDatabaseCapability.class);

    /**
     * HikariCP 数据源
     * 
     * <p>由自动配置类根据外部配置创建，封装了连接池管理逻辑。</p>
     */
    private final HikariDataSource dataSource;

    /**
     * Spring JDBC 模板
     * 
     * <p>用于执行原生 SQL 查询，封装了 JDBC 操作的模板方法。</p>
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 当前数据库方言
     * 
     * <p>由配置驱动决定，用于插件判断当前数据库类型。</p>
     */
    private final DatabaseDialect dialect;

    /**
     * 数据库名称
     */
    private final String databaseName;

    /**
     * Schema 名称
     */
    private final String schemaName;

    /**
     * 构造函数
     * 
     * @param dataSource   HikariCP 数据源（由自动配置创建）
     * @param dialect      数据库方言
     * @param databaseName 数据库名称
     * @param schemaName   Schema 名称
     */
    public HikariDatabaseCapability(
            HikariDataSource dataSource,
            DatabaseDialect dialect,
            String databaseName,
            String schemaName) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource 不能为空");
        this.dialect = Objects.requireNonNull(dialect, "dialect 不能为空");
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName 不能为空");
        this.schemaName = schemaName != null ? schemaName : "public";
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        log.info("[Database] 数据库能力初始化完成: dialect={}, database={}, schema={}",
                dialect.getDisplayName(), databaseName, this.schemaName);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>返回 HikariCP 管理的数据源实例。</p>
     */
    @Override
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
     * {@inheritDoc}
     * 
     * <p>使用 Spring JdbcTemplate 执行原生 SQL。参数通过占位符传入，防止 SQL 注入。</p>
     * 
     * @param sql        原生 SQL 语句
     * @param resultType 结果类型
     * @param params     SQL 参数
     * @param <T>        结果类型
     * @return 查询结果
     */
    @Override
    public <T> T executeNative(String sql, Class<T> resultType, Object... params) {
        Objects.requireNonNull(sql, "sql 不能为空");
        Objects.requireNonNull(resultType, "resultType 不能为空");

        log.debug("[Database] 执行原生 SQL: sql={}, dialect={}", sql, dialect.getDisplayName());
        return jdbcTemplate.queryForObject(sql, resultType, params);
    }
}
