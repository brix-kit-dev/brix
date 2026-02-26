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
 * 数据库能力契约
 * 
 * <p>提供数据库访问的抽象接口，使插件无需感知底层数据库具体类型
 * （PostgreSQL / Kingbase / MySQL / Oracle），通过配置即可切换数据库厂商。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>提供数据源（DataSource）：插件通过此接口获取数据源，无需关心连接池配置</li>
 *   <li>数据库方言抽象：插件可获取当前方言以处理特殊 SQL 语法差异</li>
 *   <li>库名/Schema 管理：支持多租户场景下的 Schema 隔离</li>
 *   <li>原生 SQL 执行：提供受控的原生查询能力</li>
 * </ul>
 * 
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>插件透明</b>：插件代码中不出现任何数据库驱动依赖（postgresql、kingbase 等）</li>
 *   <li><b>配置驱动</b>：数据库类型、连接信息由 Host 层配置管理</li>
 *   <li><b>连接池统一</b>：连接池（HikariCP）由适配器统一管理</li>
 * </ul>
 * 
 * <h3>蓝图对照</h3>
 * <p>对应蓝图 v3.0.2 第 3.3.1 节「DatabaseCapability - 数据库能力」。
 * 能力级别为 STANDARD（标准能力），推荐所有 Host 实现。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 插件通过 RuntimeContext 获取数据库能力
 * DatabaseCapability db = context.getDatabase();
 * 
 * // 获取数据源用于 JPA/JDBC
 * DataSource dataSource = db.getDataSource();
 * 
 * // 获取当前方言（处理 SQL 差异）
 * DatabaseDialect dialect = db.getDialect();
 * 
 * // 执行原生 SQL
 * Long count = db.executeNative("SELECT COUNT(*) FROM users", Long.class);
 * }</pre>
 * 
 * <h3>实现说明</h3>
 * <p>此接口由基础设施适配器层（Layer 2.5）实现：</p>
 * <ul>
 *   <li>{@code infra-adapter-database}：封装 PostgreSQL / Kingbase / MySQL 驱动</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see DatabaseDialect
 */
public interface DatabaseCapability {

    /**
     * 获取数据源
     * 
     * <p>返回当前配置的数据源实例，由适配器负责连接池管理。
     * 插件可将此数据源注入到 JPA EntityManagerFactory 或直接用于 JDBC 操作。</p>
     * 
     * <h4>⚠️ 内部 API 说明</h4>
     * <p>此方法标记为 {@code @InternalApi}，暴露了基础设施类型 {@link DataSource}。
     * 业务插件应优先使用以下推荐方式：</p>
     * <ul>
     *   <li><b>JPA 方式</b>：通过依赖注入 EntityManager 进行数据操作</li>
     *   <li><b>原生 SQL</b>：使用 {@link #executeNative(String, Class, Object...)} 方法</li>
     * </ul>
     * <p>此方法保留供 Host 适配器层和框架扩展使用。</p>
     * 
     * @return 数据源实例，不会返回 null
     */
    @InternalApi(value = "暴露基础设施类型 DataSource，业务插件应使用 JPA 或 executeNative()", 
                 instead = "executeNative")
    DataSource getDataSource();

    /**
     * 获取当前数据库方言
     * 
     * <p>用于处理不同数据库的 SQL 语法差异。大多数场景下插件使用 JPA 不需要关心方言，
     * 仅在编写原生 SQL 或需要使用数据库特殊特性时才需要查询方言。</p>
     * 
     * @return 当前数据库方言枚举
     * @see DatabaseDialect
     */
    DatabaseDialect getDialect();

    /**
     * 获取数据库名称
     * 
     * <p>返回当前连接的数据库名称，由 Host 层配置管理。</p>
     * 
     * @return 数据库名称
     */
    String getDatabaseName();

    /**
     * 获取 Schema 名称
     * 
     * <p>支持多租户场景下的 Schema 隔离，返回当前租户对应的 Schema 名称。</p>
     * 
     * @return Schema 名称
     */
    String getSchemaName();

    /**
     * 执行原生 SQL 查询
     * 
     * <p>提供受控的原生 SQL 执行能力，适用于复杂查询或数据库特定的操作。
     * 优先使用 JPA，仅在 JPA 无法满足需求时使用此方法。</p>
     * 
     * <h4>安全说明</h4>
     * <p>SQL 参数通过占位符传入，禁止拼接 SQL 字符串以防止 SQL 注入。</p>
     * 
     * @param sql        原生 SQL 语句，使用 {@code ?} 作为参数占位符
     * @param resultType 结果类型
     * @param params     SQL 参数（按顺序匹配占位符）
     * @param <T>        结果类型
     * @return 查询结果
     * @throws IllegalArgumentException 如果 SQL 为 null 或 resultType 为 null
     */
    <T> T executeNative(String sql, Class<T> resultType, Object... params);
}
