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
package io.brix.platform.tenant.exception;

/**
 * Exception thrown when a SQL query violates tenant isolation rules.
 *
 * <p>This exception is thrown by the {@code TenantSqlGuardInterceptor} when it
 * detects a database query that should include tenant filtering but doesn't.
 * This is a critical security exception that indicates a potential data leak risk.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 *
 * <h3>When This Exception Is Thrown</h3>
 * <ul>
 *   <li>A SELECT query on a business table (biz_*) lacks tenant_id condition</li>
 *   <li>An UPDATE or DELETE query on a business table lacks tenant_id condition</li>
 *   <li>A query bypasses tenant filtering without {@code @CrossTenantAccess} annotation</li>
 * </ul>
 *
 * <h3>Environment-Specific Behavior</h3>
 * <p>The interceptor behavior varies by environment:</p>
 * <ul>
 *   <li><b>Development:</b> Throws this exception immediately to fail fast</li>
 *   <li><b>Staging:</b> Throws this exception for early detection</li>
 *   <li><b>Production:</b> Logs a warning and allows the query (for stability)</li>
 * </ul>
 *
 * <h3>Common Causes</h3>
 * <ol>
 *   <li>Native SQL query without tenant condition</li>
 *   <li>Missing TenantContext initialization in async operations</li>
 *   <li>Repository method without proper tenant filtering</li>
 *   <li>JPQL/HQL query missing tenant predicate</li>
 * </ol>
 *
 * <h3>Resolution Steps</h3>
 * <ol>
 *   <li>Add tenant_id condition to the query</li>
 *   <li>Or add {@code @CrossTenantAccess(reason = "...")} if cross-tenant access is legitimate</li>
 *   <li>Or verify the table should be in the whitelist (sys_* tables)</li>
 * </ol>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // This query will trigger TenantSqlViolationException
 * @Query("SELECT c FROM Case c WHERE c.status = :status")  // Missing tenant filter!
 * List<Case> findByStatus(@Param("status") String status);
 *
 * // Correct query
 * @Query("SELECT c FROM Case c WHERE c.tenantId = :tenantId AND c.status = :status")
 * List<Case> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("status") String status);
 *
 * // Or use automatic filtering via repository convention
 * List<Case> findByStatus(String status);  // Interceptor adds tenant filter
 * }</pre>
 *
 * <h3>HTTP Response</h3>
 * <p>In development mode, this exception maps to HTTP 500 (Internal Server Error)
 * to clearly indicate a programming error that must be fixed before release.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor
 * @see io.brix.platform.tenant.annotation.CrossTenantAccess
 */
public class TenantSqlViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for HTTP response and logging.
     */
    public static final String ERROR_CODE = "TENANT_SQL_VIOLATION";

    /**
     * The SQL statement that violates tenant isolation.
     *
     * <p>In production mode, this may be truncated or masked to prevent
     * sensitive data exposure in error responses.
     */
    private final String sql;

    /**
     * The table name involved in the violation.
     *
     * <p>Extracted from the SQL statement for targeted troubleshooting.
     */
    private final String tableName;

    /**
     * The type of SQL operation (SELECT, INSERT, UPDATE, DELETE).
     */
    private final String operationType;

    /**
     * Constructs a new TenantSqlViolationException with the violating SQL.
     *
     * @param sql the SQL statement that lacks tenant filtering
     */
    public TenantSqlViolationException(String sql) {
        super(buildMessage(sql, null, null));
        this.sql = sql;
        this.tableName = null;
        this.operationType = null;
    }

    /**
     * Constructs a new TenantSqlViolationException with detailed information.
     *
     * @param sql           the SQL statement that lacks tenant filtering
     * @param tableName     the table name involved in the violation
     * @param operationType the type of SQL operation (SELECT, UPDATE, etc.)
     */
    public TenantSqlViolationException(String sql, String tableName, String operationType) {
        super(buildMessage(sql, tableName, operationType));
        this.sql = sql;
        this.tableName = tableName;
        this.operationType = operationType;
    }

    /**
     * Constructs a new TenantSqlViolationException with a custom message.
     *
     * @param message       custom error message
     * @param sql           the SQL statement that lacks tenant filtering
     * @param tableName     the table name involved in the violation
     * @param operationType the type of SQL operation
     */
    public TenantSqlViolationException(String message, String sql, String tableName, String operationType) {
        super(message);
        this.sql = sql;
        this.tableName = tableName;
        this.operationType = operationType;
    }

    /**
     * Builds the exception message with available details.
     *
     * @param sql           the violating SQL
     * @param tableName     the table name (nullable)
     * @param operationType the operation type (nullable)
     * @return formatted error message
     */
    private static String buildMessage(String sql, String tableName, String operationType) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tenant isolation violation detected: SQL query lacks tenant_id filter. ");

        if (operationType != null) {
            sb.append("Operation: ").append(operationType).append(". ");
        }

        if (tableName != null) {
            sb.append("Table: ").append(tableName).append(". ");
        }

        if (sql != null) {
            // Truncate long SQL for readability
            String truncatedSql = sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
            sb.append("SQL: ").append(truncatedSql);
        }

        return sb.toString();
    }

    /**
     * Returns the error code for this exception.
     *
     * @return the error code "TENANT_SQL_VIOLATION"
     */
    public String getErrorCode() {
        return ERROR_CODE;
    }

    /**
     * Returns the SQL statement that caused the violation.
     *
     * @return the violating SQL statement
     */
    public String getSql() {
        return sql;
    }

    /**
     * Returns the table name involved in the violation.
     *
     * @return the table name, or null if not determined
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the type of SQL operation.
     *
     * @return the operation type (SELECT, UPDATE, etc.), or null if not determined
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * Returns a sanitized version of the SQL for logging.
     *
     * <p>This version truncates long SQL and removes potentially sensitive data.
     *
     * @return sanitized SQL string
     */
    public String getSanitizedSql() {
        if (sql == null) {
            return "[no SQL captured]";
        }
        // Truncate and remove potential PII (simplified - real implementation would be more thorough)
        String sanitized = sql.replaceAll("'[^']*'", "'***'");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) + "..." : sanitized;
    }
}
