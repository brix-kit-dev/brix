/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.tenant.interceptor;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.core.TenantWhitelist;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hibernate Statement Inspector that automatically injects tenant filtering into SQL statements.
 *
 * <p>This interceptor is the core mechanism for enforcing row-level tenant isolation in Brix Platform.
 * It intercepts all SQL statements before they are executed and injects tenant_id conditions to ensure
 * that queries only access data belonging to the current tenant.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>StatementInspector vs @Filter:</b> We use StatementInspector because it provides
 *       a single point of enforcement that cannot be bypassed, unlike @Filter which requires
 *       explicit enablement per session.</li>
 *   <li><b>SQL Modification:</b> We modify SQL directly rather than using parameter binding
 *       for the tenant_id to ensure all queries are protected, including native queries.</li>
 *   <li><b>Whitelist Approach:</b> Tables are filtered by default; only explicitly whitelisted
 *       tables (sys_* tables) bypass filtering.</li>
 * </ul>
 *
 * <h3>SQL Operations Handled</h3>
 * <ul>
 *   <li><b>SELECT:</b> Adds {@code AND tenant_id = ?} to WHERE clause</li>
 *   <li><b>UPDATE:</b> Adds {@code AND tenant_id = ?} to WHERE clause</li>
 *   <li><b>DELETE:</b> Adds {@code AND tenant_id = ?} to WHERE clause</li>
 *   <li><b>INSERT:</b> Not modified (tenant_id is set by entity listener)</li>
 * </ul>
 *
 * <h3>Whitelist Tables</h3>
 * <p>The following tables bypass tenant filtering:</p>
 * <ul>
 *   <li>sys_tenant - Tenant table itself</li>
 *   <li>sys_identity - Global identity table</li>
 *   <li>sys_platform_admin - Platform administrators</li>
 *   <li>flyway_schema_history - Database migrations</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. Tenant ID is retrieved from ThreadLocal-based TenantContext.</p>
 *
 * <h3>Performance Considerations</h3>
 * <ul>
 *   <li>Regex patterns are pre-compiled for efficiency</li>
 *   <li>Whitelist check uses Set.contains() for O(1) lookup</li>
 *   <li>SQL parsing is minimalistic to avoid overhead</li>
 * </ul>
 *
 * <h3>Limitations</h3>
 * <p>This interceptor has intentional limitations to keep the implementation simple and reliable:</p>
 * <ul>
 *   <li>Complex SQL with subqueries may not be correctly modified (use native queries carefully)</li>
 *   <li>UNION queries are not supported (each part must be a separate query)</li>
 *   <li>JOIN queries assume the main table needs filtering; joined tables should also have tenant_id</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>This interceptor is configured via {@code TenantInterceptorConfig} and registered
 * through Hibernate's {@code hibernate.session_factory.statement_inspector} property.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantContext
 * @see TenantWhitelist
 * @see org.hibernate.resource.jdbc.spi.StatementInspector
 */
public class TenantInterceptor implements StatementInspector {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    /**
     * The column name used for tenant identification in business tables.
     * This is a fundamental architectural constant and should not be changed.
     */
    private static final String TENANT_COLUMN = "tenant_id";

    // ========================================================================
    // Pre-compiled Regex Patterns for SQL Parsing
    // ========================================================================

    /**
     * Pattern to extract the main table name from SELECT statements.
     * Handles: SELECT ... FROM table_name [alias] [WHERE ...]
     * 
     * Group 1: table name (with optional schema prefix)
     * 
     * Technical Note: This pattern is intentionally simple and handles common cases.
     * Complex queries with multiple FROMs or subqueries should use native queries
     * with explicit tenant filtering.
     */
    private static final Pattern SELECT_TABLE_PATTERN = Pattern.compile(
            "\\bFROM\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)" +
            "(?:\\s+(?:AS\\s+)?[a-zA-Z_][a-zA-Z0-9_]*)?",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to extract the table name from UPDATE statements.
     * Handles: UPDATE table_name [alias] SET ...
     */
    private static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile(
            "\\bUPDATE\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to extract the table name from DELETE statements.
     * Handles: DELETE FROM table_name [alias] [WHERE ...]
     */
    private static final Pattern DELETE_TABLE_PATTERN = Pattern.compile(
            "\\bDELETE\\s+FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to detect if WHERE clause already exists.
     */
    private static final Pattern WHERE_CLAUSE_PATTERN = Pattern.compile(
            "\\bWHERE\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to detect ORDER BY clause for correct tenant condition insertion.
     */
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "\\bORDER\\s+BY\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to detect GROUP BY clause for correct tenant condition insertion.
     */
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(
            "\\bGROUP\\s+BY\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to detect LIMIT clause for correct tenant condition insertion.
     */
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "\\bLIMIT\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to check if tenant_id condition already exists in the SQL.
     * This prevents double-adding tenant conditions.
     */
    private static final Pattern TENANT_CONDITION_PATTERN = Pattern.compile(
            "\\b" + TENANT_COLUMN + "\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    // ========================================================================
    // StatementInspector Implementation
    // ========================================================================

    /**
     * Inspects and potentially modifies the SQL statement before execution.
     *
     * <p>This method is called by Hibernate for every SQL statement. It performs the following:
     * <ol>
     *   <li>Determines the SQL operation type (SELECT, UPDATE, DELETE, INSERT)</li>
     *   <li>Extracts the table name from the SQL</li>
     *   <li>Checks if the table is in the whitelist</li>
     *   <li>If not whitelisted and tenant context exists, adds tenant_id condition</li>
     * </ol>
     *
     * @param sql the original SQL statement
     * @return the potentially modified SQL statement with tenant filtering
     */
    @Override
    public String inspect(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }

        if (TenantSqlGuardInterceptor.isInCrossTenantScope()) {
            return sql;
        }

        // Trim and normalize whitespace for easier parsing
        String normalizedSql = sql.trim();
        String upperSql = normalizedSql.toUpperCase(Locale.ROOT);

        // Determine operation type and process accordingly
        if (upperSql.startsWith("SELECT")) {
            return processSelectStatement(normalizedSql);
        } else if (upperSql.startsWith("UPDATE")) {
            return processUpdateStatement(normalizedSql);
        } else if (upperSql.startsWith("DELETE")) {
            return processDeleteStatement(normalizedSql);
        } else if (upperSql.startsWith("INSERT")) {
            // INSERT statements are not modified here; tenant_id is set by entity listener
            return sql;
        }

        // For other statements (DDL, etc.), pass through unchanged
        return sql;
    }

    // ========================================================================
    // SQL Processing Methods
    // ========================================================================

    /**
     * Processes SELECT statements by adding tenant_id condition.
     *
     * @param sql the original SELECT statement
     * @return the modified SQL with tenant filtering, or original if not applicable
     */
    private String processSelectStatement(String sql) {
        String tableName = extractTableName(sql, SELECT_TABLE_PATTERN);
        if (tableName == null) {
            log.trace("Could not extract table name from SELECT: {}", truncateSql(sql));
            return sql;
        }

        // Check whitelist
        if (TenantWhitelist.isWhitelisted(tableName)) {
            log.trace("Skipping tenant filter for whitelisted table: {}", tableName);
            return sql;
        }

        // Check if tenant_id condition already exists
        if (hasTenantCondition(sql)) {
            log.trace("Tenant condition already exists in SQL for table: {}", tableName);
            return sql;
        }

        // Get current tenant ID
        String tenantId = getCurrentTenantIdSafe();
        if (tenantId == null) {
            // No tenant context - this will be caught by TenantSqlGuardInterceptor
            log.trace("No tenant context for SELECT on table: {}", tableName);
            return sql;
        }

        return addTenantCondition(sql, tableName, tenantId);
    }

    /**
     * Processes UPDATE statements by adding tenant_id condition.
     *
     * @param sql the original UPDATE statement
     * @return the modified SQL with tenant filtering, or original if not applicable
     */
    private String processUpdateStatement(String sql) {
        String tableName = extractTableName(sql, UPDATE_TABLE_PATTERN);
        if (tableName == null) {
            log.trace("Could not extract table name from UPDATE: {}", truncateSql(sql));
            return sql;
        }

        // Check whitelist
        if (TenantWhitelist.isWhitelisted(tableName)) {
            log.trace("Skipping tenant filter for whitelisted table: {}", tableName);
            return sql;
        }

        // Check if tenant_id condition already exists
        if (hasTenantCondition(sql)) {
            log.trace("Tenant condition already exists in UPDATE for table: {}", tableName);
            return sql;
        }

        // Get current tenant ID
        String tenantId = getCurrentTenantIdSafe();
        if (tenantId == null) {
            log.trace("No tenant context for UPDATE on table: {}", tableName);
            return sql;
        }

        return addTenantCondition(sql, tableName, tenantId);
    }

    /**
     * Processes DELETE statements by adding tenant_id condition.
     *
     * @param sql the original DELETE statement
     * @return the modified SQL with tenant filtering, or original if not applicable
     */
    private String processDeleteStatement(String sql) {
        String tableName = extractTableName(sql, DELETE_TABLE_PATTERN);
        if (tableName == null) {
            log.trace("Could not extract table name from DELETE: {}", truncateSql(sql));
            return sql;
        }

        // Check whitelist
        if (TenantWhitelist.isWhitelisted(tableName)) {
            log.trace("Skipping tenant filter for whitelisted table: {}", tableName);
            return sql;
        }

        // Check if tenant_id condition already exists
        if (hasTenantCondition(sql)) {
            log.trace("Tenant condition already exists in DELETE for table: {}", tableName);
            return sql;
        }

        // Get current tenant ID
        String tenantId = getCurrentTenantIdSafe();
        if (tenantId == null) {
            log.trace("No tenant context for DELETE on table: {}", tableName);
            return sql;
        }

        return addTenantCondition(sql, tableName, tenantId);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Extracts the table name from SQL using the provided pattern.
     *
     * <p>Handles schema-qualified table names (e.g., public.biz_case) by extracting
     * only the table name portion.
     *
     * @param sql     the SQL statement
     * @param pattern the regex pattern for extraction
     * @return the table name (without schema), or null if not found
     */
    private String extractTableName(String sql, Pattern pattern) {
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            String fullName = matcher.group(1);
            // Handle schema.table format
            int dotIndex = fullName.lastIndexOf('.');
            if (dotIndex >= 0) {
                return fullName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
            }
            return fullName.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /**
     * Checks if the SQL already contains a tenant_id condition.
     *
     * @param sql the SQL statement
     * @return true if tenant_id condition exists
     */
    private boolean hasTenantCondition(String sql) {
        return TENANT_CONDITION_PATTERN.matcher(sql).find();
    }

    /**
     * Gets the current tenant ID safely, returning null if not set.
     *
     * @return the current tenant ID, or null if no tenant context
     */
    private String getCurrentTenantIdSafe() {
        return TenantContext.getTenantId().orElse(null);
    }

    /**
     * Adds tenant_id condition to the SQL statement.
     *
     * <p>This method intelligently inserts the tenant condition:
     * <ul>
     *   <li>If WHERE clause exists, adds {@code AND tenant_id = 'xxx'}</li>
     *   <li>If no WHERE clause, adds {@code WHERE tenant_id = 'xxx'} before ORDER BY/GROUP BY/LIMIT</li>
     * </ul>
     *
     * @param sql       the original SQL
     * @param tableName the table name (for alias resolution)
     * @param tenantId  the tenant ID to filter by
     * @return the modified SQL with tenant condition
     */
    private String addTenantCondition(String sql, String tableName, String tenantId) {
        // Build the tenant condition
        // Note: Using string concatenation for tenant_id is safe here because:
        // 1. tenantId comes from TenantContext which is set by our trusted filters
        // 2. We escape single quotes in the tenant ID value
        String safeTenantId = escapeSqlString(tenantId);
        String tenantCondition = TENANT_COLUMN + " = '" + safeTenantId + "'";

        // Check if WHERE clause exists
        Matcher whereMatcher = WHERE_CLAUSE_PATTERN.matcher(sql);

        if (whereMatcher.find()) {
            // WHERE exists - add AND condition after WHERE
            int whereEnd = whereMatcher.end();
            
            // Find the next significant clause (GROUP BY, ORDER BY, LIMIT)
            // and insert before it, or at the end of conditions
            String beforeWhere = sql.substring(0, whereEnd);
            String afterWhere = sql.substring(whereEnd);
            
            // Insert tenant condition right after WHERE keyword
            return beforeWhere + " " + tenantCondition + " AND" + afterWhere;
        } else {
            // No WHERE clause - need to add one
            // Insert before ORDER BY, GROUP BY, or LIMIT, or at the end
            
            int insertPosition = findInsertPosition(sql);
            String beforeInsert = sql.substring(0, insertPosition);
            String afterInsert = sql.substring(insertPosition);
            
            return beforeInsert + " WHERE " + tenantCondition + afterInsert;
        }
    }

    /**
     * Finds the position to insert WHERE clause when no WHERE exists.
     *
     * <p>Looks for ORDER BY, GROUP BY, or LIMIT clauses and returns
     * the position just before the first one found.
     *
     * @param sql the SQL statement
     * @return the position to insert WHERE clause
     */
    private int findInsertPosition(String sql) {
        int position = sql.length();

        // Check for ORDER BY
        Matcher orderMatcher = ORDER_BY_PATTERN.matcher(sql);
        if (orderMatcher.find()) {
            position = Math.min(position, orderMatcher.start());
        }

        // Check for GROUP BY
        Matcher groupMatcher = GROUP_BY_PATTERN.matcher(sql);
        if (groupMatcher.find()) {
            position = Math.min(position, groupMatcher.start());
        }

        // Check for LIMIT
        Matcher limitMatcher = LIMIT_PATTERN.matcher(sql);
        if (limitMatcher.find()) {
            position = Math.min(position, limitMatcher.start());
        }

        return position;
    }

    /**
     * Escapes single quotes in SQL string values to prevent SQL injection.
     *
     * @param value the string value to escape
     * @return the escaped string
     */
    private String escapeSqlString(String value) {
        if (value == null) {
            return "";
        }
        // Escape single quotes by doubling them (SQL standard)
        return value.replace("'", "''");
    }

    /**
     * Truncates SQL for logging to avoid log flooding.
     *
     * @param sql the SQL to truncate
     * @return truncated SQL string
     */
    private String truncateSql(String sql) {
        if (sql == null) {
            return "[null]";
        }
        return sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
    }
}
