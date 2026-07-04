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
package io.brix.platform.tenant.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tenant Whitelist for tables that don't require tenant_id filtering.
 *
 * <p>In a multi-tenant system, most tables require tenant_id filtering in all queries.
 * However, some system-level tables exist outside the tenant boundary and should be
 * excluded from automatic tenant filtering.
 *
 * <h3>Purpose</h3>
 * <p>This whitelist is used by:
 * <ul>
 *   <li>MyBatis interceptors to skip tenant injection</li>
 *   <li>SQL Guard to skip tenant column validation</li>
 *   <li>Architecture tests to verify table classification</li>
 * </ul>
 *
 * <h3>Whitelisted Table Categories</h3>
 * <ol>
 *   <li><b>Platform-global Tables:</b> Explicitly listed tables without tenant scope</li>
 *   <li><b>Infrastructure Tables:</b> Flyway history, etc.</li>
 * </ol>
 *
 * <h3>Table Naming Convention</h3>
 * <ul>
 *   <li><code>sys_*</code> - Platform tables; only explicit global tables are whitelisted</li>
 *   <li><code>biz_*</code> - Business tables (require tenant_id)</li>
 *   <li><code>cfg_*</code> - Configuration tables (may or may not have tenant_id)</li>
 * </ul>
 *
 * <h3>Security Note</h3>
 * <p>Adding a table to this whitelist bypasses tenant isolation checks.
 * Only add tables that are truly tenant-agnostic. Incorrectly whitelisting
 * a business table can lead to data leaks.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * String tableName = "sys_tenant";
 * if (TenantWhitelist.isWhitelisted(tableName)) {
 *     // Skip tenant filtering
 * } else {
 *     // Apply tenant_id = ? condition
 * }
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public final class TenantWhitelist {

    /**
     * Set of table names that are excluded from tenant filtering.
     *
     * <p>This set is immutable and thread-safe.
     */
    private static final Set<String> WHITELISTED_TABLES;

    static {
        Set<String> tables = new HashSet<>();
        
        // ==================================================================
        // System Tables (Platform-level, no tenant scope)
        // ==================================================================
        
        /*
         * sys_tenant: The tenant table itself cannot have tenant_id
         * as it defines the tenants.
         */
        tables.add("sys_tenant");
        
        /*
         * sys_identity: Global identity table.
         * Users exist at platform level and can join multiple tenants.
         */
        tables.add("sys_identity");
        
        /*
         * sys_platform_admin: Platform administrator accounts.
         * Platform admins operate across all tenants.
         */
        tables.add("sys_platform_admin");

        /*
         * sys_platform_config: platform-wide configuration without tenant_id.
         */
        tables.add("sys_platform_config");

        /*
         * sys_installation_quota: instance-level quota table without tenant_id.
         */
        tables.add("sys_installation_quota");

        /*
         * sys_platform_audit_log: platform audit records may span tenants and
         * store affected_tenants instead of a single tenant_id.
         */
        tables.add("sys_platform_audit_log");

        /*
         * auth_refresh_token: Platform identity-scoped token table.
         * It has no tenant_id column by design, so it is explicitly exempt.
         */
        tables.add("auth_refresh_token");
        
        /*
         * sys_organization: While org has tenant_id, it's often queried
         * at the system level for cross-tenant analytics.
         * 
         * IMPORTANT: Most queries SHOULD include tenant filter.
         * Only whitelisted for specific cross-tenant operations.
         * Repository methods should still filter by tenant_id explicitly.
         */
        // Note: sys_organization is NOT whitelisted - it has tenant_id and
        // should use tenant filtering. Un-comment only if cross-tenant
        // queries are explicitly required.
        // tables.add("sys_organization");
        
        // ==================================================================
        // Infrastructure Tables
        // ==================================================================
        
        /*
         * Flyway schema history table.
         * Used for database migration tracking.
         */
        tables.add("flyway_schema_history");
        
        /*
         * JPA/Hibernate metadata tables (if any).
         */
        tables.add("hibernate_sequence");
        tables.add("hibernate_sequences");
        
        WHITELISTED_TABLES = Collections.unmodifiableSet(tables);
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private TenantWhitelist() {
        throw new UnsupportedOperationException(
            "TenantWhitelist is a utility class and cannot be instantiated"
        );
    }

    /**
     * Checks if a table is whitelisted (exempt from tenant filtering).
     *
     * <p>Table name matching is case-insensitive.
     *
     * @param tableName the table name to check
     * @return true if the table is whitelisted
     */
    public static boolean isWhitelisted(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        return WHITELISTED_TABLES.contains(tableName.toLowerCase().trim());
    }

    /**
     * Checks if a table requires tenant filtering.
     *
     * <p>This is the inverse of {@link #isWhitelisted(String)}.
     *
     * @param tableName the table name to check
     * @return true if the table requires tenant_id filtering
     */
    public static boolean requiresTenantFilter(String tableName) {
        return !isWhitelisted(tableName);
    }

    /**
     * Returns an unmodifiable view of all whitelisted table names.
     *
     * <p>Useful for documentation, logging, and architecture tests.
     *
     * @return set of whitelisted table names (lowercase)
     */
    public static Set<String> getWhitelistedTables() {
        return WHITELISTED_TABLES;
    }

    /**
     * Checks if the table name follows system table naming convention.
     *
     * <p>System tables start with "sys_" prefix.
     *
     * @param tableName the table name to check
     * @return true if table name starts with "sys_"
     */
    public static boolean isSystemTableName(String tableName) {
        if (tableName == null) {
            return false;
        }
        return tableName.toLowerCase().startsWith("sys_");
    }

    /**
     * Checks if the table name follows business table naming convention.
     *
     * <p>Business tables start with "biz_" prefix and should have tenant_id.
     *
     * @param tableName the table name to check
     * @return true if table name starts with "biz_"
     */
    public static boolean isBusinessTableName(String tableName) {
        if (tableName == null) {
            return false;
        }
        return tableName.toLowerCase().startsWith("biz_");
    }

    /**
     * Validates that a business table is not whitelisted.
     *
     * <p>This is a sanity check to ensure business tables are not
     * accidentally added to the whitelist.
     *
     * @param tableName the table name to validate
     * @throws IllegalStateException if a business table is whitelisted
     */
    public static void validateNoBusinessTableWhitelisted(String tableName) {
        if (isBusinessTableName(tableName) && isWhitelisted(tableName)) {
            throw new IllegalStateException(
                "Business table '" + tableName + "' should not be whitelisted. " +
                "Business tables must have tenant isolation."
            );
        }
    }
}
