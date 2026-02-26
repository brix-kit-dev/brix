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
 * 数据库方言枚举
 * 
 * <p>定义平台支持的数据库厂商标识。
 * 用于配置驱动的多数据库厂商切换，对应蓝图 v3.0.2 第 3.3.1 节。</p>
 * 
 * <h3>厂商支持</h3>
 * <table border="1">
 *   <tr><th>厂商</th><th>备注</th></tr>
 *   <tr><td>PostgreSQL</td><td>主推</td></tr>
 *   <tr><td>Kingbase</td><td>兼容模式</td></tr>
 *   <tr><td>MySQL</td><td>预留</td></tr>
 *   <tr><td>Oracle</td><td>预留</td></tr>
 * </table>
 * 
 * <h3>使用方式</h3>
 * <pre>{@code
 * DatabaseDialect dialect = databaseCapability.getDialect();
 * if (dialect == DatabaseDialect.KINGBASE) {
 *     // 处理 Kingbase 特有的 SQL 语法
 * }
 * }</pre>
 * 
 * <p><b>注意</b>：Hibernate 方言类名和 JDBC 驱动类名属于基础设施细节，
 * 由 adapter 层（infra-adapter-database）负责映射，不在契约层暴露。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see DatabaseCapability
 */
public enum DatabaseDialect {

    /**
     * PostgreSQL 数据库
     * 
     * <p>平台主推的关系型数据库。</p>
     */
    POSTGRESQL("PostgreSQL"),

    /**
     * 人大金仓（Kingbase）数据库
     * 
     * <p>国产数据库，使用 PostgreSQL 兼容模式。
     * Kingbase V8 在 SQL 语法层面与 PostgreSQL 高度兼容。</p>
     */
    KINGBASE("Kingbase"),

    /**
     * MySQL 数据库（预留）
     */
    MYSQL("MySQL"),

    /**
     * Oracle 数据库（预留）
     */
    ORACLE("Oracle");

    /** 数据库显示名称 */
    private final String displayName;

    DatabaseDialect(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取数据库显示名称
     * 
     * @return 数据库显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据名称查找方言枚举（忽略大小写）
     * 
     * @param name 方言名称
     * @return 匹配的方言枚举
     * @throws IllegalArgumentException 如果名称不匹配任何方言
     */
    public static DatabaseDialect fromName(String name) {
        for (DatabaseDialect dialect : values()) {
            if (dialect.name().equalsIgnoreCase(name) 
                    || dialect.displayName.equalsIgnoreCase(name)) {
                return dialect;
            }
        }
        throw new IllegalArgumentException("不支持的数据库方言: " + name 
                + "，支持的方言: POSTGRESQL, KINGBASE, MYSQL, ORACLE");
    }
}
