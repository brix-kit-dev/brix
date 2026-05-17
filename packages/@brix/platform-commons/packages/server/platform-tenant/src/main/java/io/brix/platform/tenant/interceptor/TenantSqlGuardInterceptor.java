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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.core.TenantWhitelist;
import io.brix.platform.tenant.exception.TenantSqlViolationException;

/**
 * Hibernate Statement Inspector that guards against SQL queries missing tenant filtering.
 *
 * <p>This interceptor acts as a security guard that detects SQL statements accessing
 * business tables without proper tenant_id conditions. Unlike {@link TenantInterceptor}
 * which automatically adds tenant conditions, this guard validates that tenant isolation
 * is properly maintained and fails fast when violations are detected.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 *
 * <h3>Purpose</h3>
 * <p>This guard serves multiple purposes:</p>
 * <ul>
 *   <li><b>Development Safety Net:</b> Catches missing tenant filters early in development</li>
 *   <li><b>Security Audit:</b> Logs potential tenant isolation breaches for review</li>
 *   <li><b>Compliance:</b> Ensures adherence to multi-tenant data isolation requirements</li>
 *   <li><b>Documentation:</b> Forces developers to use @CrossTenantAccess when needed</li>
 * </ul>
 *
 * <h3>Environment-Specific Behavior</h3>
 * <table border="1">
 *   <tr><th>Environment</th><th>Missing Tenant Filter</th><th>Action</th></tr>
 *   <tr><td>Development</td><td>Detected</td><td>Throws TenantSqlViolationException</td></tr>
 *   <tr><td>Staging</td><td>Detected</td><td>Throws TenantSqlViolationException</td></tr>
 *   <tr><td>Production</td><td>Detected</td><td>Logs WARNING, allows query to proceed</td></tr>
 * </table>
 *
 * <h3>What Is Checked</h3>
 * <ul>
 *   <li>SELECT statements on business tables (not in whitelist)</li>
 *   <li>UPDATE statements on business tables</li>
 *   <li>DELETE statements on business tables</li>
 *   <li>Presence of tenant_id in WHERE clause</li>
 * </ul>
 *
 * <h3>Integration with @CrossTenantAccess</h3>
 * <p>When a method is annotated with {@link CrossTenantAccess}, this guard will:
 * <ol>
 *   <li>Log the cross-tenant access with the stated reason</li>
 *   <li>Allow the query to proceed without tenant filtering</li>
 *   <li>Record the access in the audit trail (if audit logging is enabled)</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <p>The guard mode is configured via application properties:</p>
 * <pre>{@code
 * brix:
 *   tenant:
 *     guard:
 *       enabled: true              # Enable/disable the guard
 *       fail-on-violation: true    # Throw exception or just log
 *       log-level: WARN            # Log level for violations
 * }</pre>
 *
 * <h3>Order of Execution</h3>
 * <p>This guard runs BEFORE {@link TenantInterceptor} to catch violations before
 * tenant conditions are automatically added. The interceptor chain is:
 * <ol>
 *   <li>TenantSqlGuardInterceptor - Validates tenant filtering</li>
 *   <li>TenantInterceptor - Adds tenant conditions</li>
 *   <li>Hibernate Core - Executes query</li>
 * </ol>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. All state is either immutable or accessed via ThreadLocal.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantInterceptor
 * @see CrossTenantAccess
 * @see TenantSqlViolationException
 */
public class TenantSqlGuardInterceptor implements StatementInspector {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(TenantSqlGuardInterceptor.class);

    /**
     * The column name used for tenant identification.
     */
    private static final String TENANT_COLUMN = "tenant_id";

    // ========================================================================
    // ThreadLocal for Cross-Tenant Access Context
    // ========================================================================

    /**
     * ThreadLocal to track if current execution is within @CrossTenantAccess scope.
     *
     * <p>This is set by AOP aspects or filter chains when @CrossTenantAccess is detected.
     */
    private static final ThreadLocal<CrossTenantAccessContext> CROSS_TENANT_CONTEXT = new ThreadLocal<>();

    // ========================================================================
    // Configuration (injected via constructor or setter)
    // ========================================================================

    /**
     * Whether to throw exception on violation (true) or just log (false).
     * In production, this should typically be false for stability.
     */
    private final boolean failOnViolation;

    /**
     * Whether the guard is enabled. Useful for disabling in certain test scenarios.
     */
    private final boolean enabled;

    // ========================================================================
    // Pre-compiled Patterns
    // ========================================================================

    /**
     * Pattern to extract table name from SELECT statements.
     */
    private static final Pattern SELECT_TABLE_PATTERN = Pattern.compile(
            "\\bFROM\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to extract table name from UPDATE statements.
     */
    private static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile(
            "\\bUPDATE\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to extract table name from DELETE statements.
     */
    private static final Pattern DELETE_TABLE_PATTERN = Pattern.compile(
            "\\bDELETE\\s+FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to detect tenant_id in WHERE clause.
     */
    private static final Pattern TENANT_CONDITION_PATTERN = Pattern.compile(
            "\\bWHERE\\b[^;]*\\b" + TENANT_COLUMN + "\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to detect business tables (biz_* prefix).
     */
    private static final Pattern BUSINESS_TABLE_PATTERN = Pattern.compile(
            "^biz_",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WRITE_STATEMENT_PATTERN = Pattern.compile(
            "^\\s*(INSERT|UPDATE|DELETE|MERGE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Creates a TenantSqlGuardInterceptor with default settings.
     *
     * <p>Default behavior is:
     * <ul>
     *   <li>Enabled</li>
     *   <li>Fail on violation (suitable for development)</li>
     * </ul>
     */
    public TenantSqlGuardInterceptor() {
        this(true, true);
    }

    /**
     * Creates a TenantSqlGuardInterceptor with specific settings.
     *
     * @param enabled         whether the guard is enabled
     * @param failOnViolation whether to throw exception on violation
     */
    public TenantSqlGuardInterceptor(boolean enabled, boolean failOnViolation) {
        this.enabled = enabled;
        this.failOnViolation = failOnViolation;
        
        log.info("TenantSqlGuardInterceptor initialized: enabled={}, failOnViolation={}", 
                 enabled, failOnViolation);
    }

    // ========================================================================
    // Cross-Tenant Access Context Management
    // ========================================================================

    /**
     * Marks the current thread as executing within @CrossTenantAccess scope.
     *
     * <p>This should be called by AOP aspects or filters when entering a method
     * annotated with @CrossTenantAccess.
     *
     * @param reason the documented reason for cross-tenant access
     */
    public static void enterCrossTenantScope(String reason) {
        enterCrossTenantScope(reason, "programmatic-cross-tenant-scope", false);
    }

    /**
     * Marks the current thread as executing within an approved cross-tenant scope.
     *
     * @param reason   documented reason for cross-tenant access
     * @param approval approval or architecture decision record reference
     * @param readOnly whether this scope only permits read statements
     */
    public static void enterCrossTenantScope(String reason, String approval, boolean readOnly) {
        if (reason == null || reason.isBlank() || approval == null || approval.isBlank()) {
            throw new IllegalArgumentException("Cross-tenant scope requires non-blank reason and approval");
        }
        CROSS_TENANT_CONTEXT.set(new CrossTenantAccessContext(reason, approval, readOnly));
        log.debug("Entering cross-tenant scope: reason={}, approval={}, readOnly={}",
                reason, approval, readOnly);
    }

    /**
     * Exits the cross-tenant access scope.
     *
     * <p>This should be called in a finally block after the cross-tenant operation completes.
     */
    public static void exitCrossTenantScope() {
        CROSS_TENANT_CONTEXT.remove();
        log.debug("Exiting cross-tenant scope");
    }

    /**
     * Checks if current thread is within @CrossTenantAccess scope.
     *
     * @return true if cross-tenant access is currently allowed
     */
    public static boolean isInCrossTenantScope() {
        return CROSS_TENANT_CONTEXT.get() != null;
    }

    // ========================================================================
    // StatementInspector Implementation
    // ========================================================================

    /**
     * Inspects SQL statements and validates tenant filtering.
     *
     * <p>This method checks if the SQL statement properly includes tenant filtering
     * for business tables. If a violation is detected, it either throws an exception
     * (in development) or logs a warning (in production).
     *
     * @param sql the SQL statement to inspect
     * @return the original SQL (unmodified - this guard only validates)
     * @throws TenantSqlViolationException if violation detected and failOnViolation is true
     */
    @Override
    public String inspect(String sql) {
        if (!enabled || sql == null || sql.isBlank()) {
            return sql;
        }

        // Skip if in cross-tenant scope
        if (isInCrossTenantScope()) {
            CrossTenantAccessContext ctx = CROSS_TENANT_CONTEXT.get();
            if (ctx.isReadOnly() && isWriteStatement(sql)) {
                throw new TenantSqlViolationException(sql, "cross-tenant-write", "WRITE");
            }
            log.trace("Allowing approved cross-tenant query: reason='{}', approval='{}', readOnly={}, sql={}",
                      ctx.getReason(), ctx.getApproval(), ctx.isReadOnly(), truncateSql(sql));
            return sql;
        }

        // Determine operation type
        String normalizedSql = sql.trim();
        String upperSql = normalizedSql.toUpperCase(Locale.ROOT);

        String tableName = null;
        String operationType = null;

        if (upperSql.startsWith("SELECT")) {
            tableName = extractTableName(normalizedSql, SELECT_TABLE_PATTERN);
            operationType = "SELECT";
        } else if (upperSql.startsWith("UPDATE")) {
            tableName = extractTableName(normalizedSql, UPDATE_TABLE_PATTERN);
            operationType = "UPDATE";
        } else if (upperSql.startsWith("DELETE")) {
            tableName = extractTableName(normalizedSql, DELETE_TABLE_PATTERN);
            operationType = "DELETE";
        } else {
            // INSERT and other statements are not checked here
            return sql;
        }

        // Skip if table name couldn't be extracted
        if (tableName == null) {
            log.trace("Could not extract table name, skipping guard check: {}", truncateSql(sql));
            return sql;
        }

        // Skip whitelisted tables
        if (TenantWhitelist.isWhitelisted(tableName)) {
            return sql;
        }

        // Check if this is a business table that requires tenant filtering
        if (!requiresTenantFiltering(tableName)) {
            return sql;
        }

        // Check if tenant_id condition exists
        if (hasTenantCondition(normalizedSql)) {
            return sql;
        }

        // Check if TenantContext is set (TenantInterceptor will add the condition)
        if (TenantContext.hasTenant()) {
            // TenantInterceptor will handle adding the condition
            log.trace("TenantContext present, TenantInterceptor will add condition for: {}", tableName);
            return sql;
        }

        // VIOLATION: Business table query without tenant filtering
        handleViolation(sql, tableName, operationType);

        // Return original SQL (if we didn't throw)
        return sql;
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Extracts the table name from SQL using the provided pattern.
     *
     * @param sql     the SQL statement
     * @param pattern the regex pattern for extraction
     * @return the table name, or null if not found
     */
    private String extractTableName(String sql, Pattern pattern) {
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            String fullName = matcher.group(1);
            int dotIndex = fullName.lastIndexOf('.');
            if (dotIndex >= 0) {
                return fullName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
            }
            return fullName.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /**
     * Checks if the SQL contains a tenant_id condition in the WHERE clause.
     *
     * @param sql the SQL statement
     * @return true if tenant filtering is present
     */
    private boolean hasTenantCondition(String sql) {
        return TENANT_CONDITION_PATTERN.matcher(sql).find();
    }

    /**
     * Determines if a table requires tenant filtering.
     *
     * <p>Currently checks for:
     * <ul>
     *   <li>Tables with biz_* prefix (business tables)</li>
     *   <li>Tables not in the whitelist</li>
     * </ul>
     *
     * @param tableName the table name to check
     * @return true if tenant filtering is required
     */
    private boolean requiresTenantFiltering(String tableName) {
        // Business tables (biz_*) always require tenant filtering
        if (BUSINESS_TABLE_PATTERN.matcher(tableName).find()) {
            return true;
        }

        if ("sys_tenant_member".equals(tableName) || "sys_tenant_principal".equals(tableName)) {
            return true;
        }

        // Non-whitelisted tables that are not sys_* / auth_* also require filtering.
        // auth_* tables (e.g. auth_refresh_token) are platform-level identity-scoped
        // tables — they have identity_id but no tenant_id by design, so tenant
        // filtering is not applicable. They mirror the sys_* exclusion.
        // This catches custom tables that should have tenant isolation.
        // Globally-scoped identity tables must be explicitly whitelisted one by
        // one; auth_* is not a blanket exemption.
        return !tableName.startsWith("sys_") &&
               !tableName.startsWith("flyway_") &&
               !tableName.startsWith("hibernate_");
    }

    private boolean isWriteStatement(String sql) {
        return WRITE_STATEMENT_PATTERN.matcher(sql).find();
    }

    /**
     * Handles a tenant filtering violation.
     *
     * @param sql           the violating SQL
     * @param tableName     the table name
     * @param operationType the SQL operation type
     * @throws TenantSqlViolationException if failOnViolation is true
     */
    private void handleViolation(String sql, String tableName, String operationType) {
        String message = String.format(
                "Tenant isolation violation: %s on table '%s' without tenant_id filter. " +
                "Either add tenant_id condition, use @CrossTenantAccess annotation, or verify TenantContext is set.",
                operationType, tableName
        );

        if (failOnViolation) {
            log.error("TENANT_GUARD_VIOLATION: {} SQL: {}", message, truncateSql(sql));
            throw new TenantSqlViolationException(sql, tableName, operationType);
        } else {
            // Production mode: log warning but allow query
            log.warn("TENANT_GUARD_WARNING: {} SQL: {}", message, truncateSql(sql));
        }
    }

    /**
     * Truncates SQL for logging.
     *
     * @param sql the SQL to truncate
     * @return truncated SQL string
     */
    private String truncateSql(String sql) {
        if (sql == null) {
            return "[null]";
        }
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }

    // ========================================================================
    // Inner Classes
    // ========================================================================

    /**
     * Context holder for cross-tenant access scope.
     */
    private static class CrossTenantAccessContext {
        private final String reason;
        private final String approval;
        private final boolean readOnly;
        private final long enteredAt;

        CrossTenantAccessContext(String reason, String approval, boolean readOnly) {
            this.reason = reason;
            this.approval = approval;
            this.readOnly = readOnly;
            this.enteredAt = System.currentTimeMillis();
        }

        String getReason() {
            return reason;
        }

        String getApproval() {
            return approval;
        }

        boolean isReadOnly() {
            return readOnly;
        }

        long getEnteredAt() {
            return enteredAt;
        }
    }
}
