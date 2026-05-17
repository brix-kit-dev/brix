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
package io.brix.platform.tenant;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.core.TenantWhitelist;
import io.brix.platform.tenant.interceptor.TenantInterceptor;
import io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantInterceptor} (Hibernate StatementInspector).
 *
 * <p>This test class validates the SQL modification behavior of the tenant
 * interceptor, ensuring that tenant_id conditions are correctly added to
 * SELECT, UPDATE, and DELETE statements while properly handling whitelist tables.
 *
 * <h3>Test Categories</h3>
 * <ul>
 *   <li>SELECT Statement Tests - Tests for automatic WHERE clause injection</li>
 *   <li>UPDATE Statement Tests - Tests for UPDATE with tenant filtering</li>
 *   <li>DELETE Statement Tests - Tests for DELETE with tenant filtering</li>
 *   <li>Whitelist Tests - Tests that whitelisted tables are not filtered</li>
 *   <li>Edge Case Tests - Tests for complex SQL patterns</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@DisplayName("TenantInterceptor Tests")
class TenantInterceptorTest {

    private TenantInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TenantInterceptor();
        TenantContext.setTenantId("tenant-123");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        if (TenantSqlGuardInterceptor.isInCrossTenantScope()) {
            TenantSqlGuardInterceptor.exitCrossTenantScope();
        }
    }

    // =========================================================================
    // SELECT Statement Tests
    // =========================================================================

    @Nested
    @DisplayName("SELECT Statement Tests")
    class SelectStatementTests {

        @Test
        @DisplayName("should add tenant condition to SELECT without WHERE")
        void shouldAddTenantConditionToSelectWithoutWhere() {
            // Given
            String sql = "SELECT * FROM biz_case";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("WHERE tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should add tenant condition to SELECT with existing WHERE")
        void shouldAddTenantConditionToSelectWithWhere() {
            // Given
            String sql = "SELECT * FROM biz_case WHERE status = 'OPEN'";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
            assertTrue(modified.contains("AND"));
            assertTrue(modified.contains("status = 'OPEN'"));
        }

        @Test
        @DisplayName("should handle SELECT with ORDER BY")
        void shouldHandleSelectWithOrderBy() {
            // Given
            String sql = "SELECT * FROM biz_order ORDER BY created_at DESC";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            // Tenant condition should be before ORDER BY
            int whereIndex = modified.indexOf("WHERE");
            int orderByIndex = modified.indexOf("ORDER BY");
            assertTrue(whereIndex < orderByIndex, "WHERE should come before ORDER BY");
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should handle SELECT with GROUP BY")
        void shouldHandleSelectWithGroupBy() {
            // Given
            String sql = "SELECT status, COUNT(*) FROM biz_order GROUP BY status";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            int whereIndex = modified.indexOf("WHERE");
            int groupByIndex = modified.indexOf("GROUP BY");
            assertTrue(whereIndex < groupByIndex, "WHERE should come before GROUP BY");
        }

        @Test
        @DisplayName("should not duplicate tenant condition if already present")
        void shouldNotDuplicateTenantCondition() {
            // Given - SQL already has tenant_id condition
            String sql = "SELECT * FROM biz_case WHERE tenant_id = 'tenant-123' AND status = 'OPEN'";

            // When
            String modified = interceptor.inspect(sql);

            // Then - should not add another tenant_id condition
            int count = countOccurrences(modified, "tenant_id");
            assertEquals(1, count, "Should have exactly one tenant_id reference");
        }

        @Test
        @DisplayName("should handle SELECT with table alias")
        void shouldHandleSelectWithTableAlias() {
            // Given
            String sql = "SELECT c.id, c.name FROM biz_case c WHERE c.status = 'OPEN'";

            // When
            String modified = interceptor.inspect(sql);

            // Then - should add tenant condition
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should handle SELECT with schema prefix")
        void shouldHandleSelectWithSchemaPrefix() {
            // Given
            String sql = "SELECT * FROM public.biz_case WHERE status = 'OPEN'";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }
    }

    // =========================================================================
    // UPDATE Statement Tests
    // =========================================================================

    @Nested
    @DisplayName("UPDATE Statement Tests")
    class UpdateStatementTests {

        @Test
        @DisplayName("should add tenant condition to UPDATE")
        void shouldAddTenantConditionToUpdate() {
            // Given
            String sql = "UPDATE biz_case SET status = 'CLOSED' WHERE id = 1";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should not modify UPDATE on whitelisted table")
        void shouldNotModifyUpdateOnWhitelistedTable() {
            // Given
            String sql = "UPDATE sys_tenant SET name = 'New Name' WHERE id = 1";

            // When
            String modified = interceptor.inspect(sql);

            // Then - should not modify sys_tenant
            assertFalse(modified.contains("tenant_id = 'tenant-123'"));
        }
    }

    // =========================================================================
    // DELETE Statement Tests
    // =========================================================================

    @Nested
    @DisplayName("DELETE Statement Tests")
    class DeleteStatementTests {

        @Test
        @DisplayName("should add tenant condition to DELETE")
        void shouldAddTenantConditionToDelete() {
            // Given
            String sql = "DELETE FROM biz_case WHERE id = 1";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should add WHERE clause to DELETE without WHERE")
        void shouldAddWhereClauseToDeleteWithoutWhere() {
            // Given
            String sql = "DELETE FROM biz_old_data";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("WHERE tenant_id = 'tenant-123'"));
        }
    }

    // =========================================================================
    // INSERT Statement Tests
    // =========================================================================

    @Nested
    @DisplayName("INSERT Statement Tests")
    class InsertStatementTests {

        @Test
        @DisplayName("should not modify INSERT statements")
        void shouldNotModifyInsertStatements() {
            // Given - INSERT is not modified by interceptor (entity listener handles this)
            String sql = "INSERT INTO biz_case (name, status) VALUES ('Test', 'NEW')";

            // When
            String modified = interceptor.inspect(sql);

            // Then - should return original SQL
            assertEquals(sql, modified);
        }
    }

    // =========================================================================
    // Whitelist Table Tests
    // =========================================================================

    @Nested
    @DisplayName("Whitelist Table Tests")
    class WhitelistTableTests {

        @Test
        @DisplayName("should not filter sys_tenant table")
        void shouldNotFilterSysTenantTable() {
            // Given
            String sql = "SELECT * FROM sys_tenant WHERE id = 1";

            // When
            String modified = interceptor.inspect(sql);

            // Then - should not add tenant condition
            assertFalse(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should not filter sys_identity table")
        void shouldNotFilterSysIdentityTable() {
            // Given
            String sql = "SELECT * FROM sys_identity WHERE phone = '123456'";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertFalse(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should filter sys_tenant_member table by default")
        void shouldFilterSysTenantMemberTableByDefault() {
            // Given
            String sql = "SELECT * FROM sys_tenant_member WHERE identity_id = 1";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should filter sys_tenant_principal table by default")
        void shouldFilterSysTenantPrincipalTableByDefault() {
            // Given
            String sql = "SELECT * FROM sys_tenant_principal WHERE identity_id = 1";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should not filter explicitly whitelisted auth refresh token table")
        void shouldNotFilterAuthRefreshTokenTable() {
            // Given
            String sql = "SELECT * FROM auth_refresh_token WHERE identity_id = 1";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertFalse(modified.contains("tenant_id = 'tenant-123'"));
            assertTrue(TenantWhitelist.isWhitelisted("auth_refresh_token"));
        }

        @Test
        @DisplayName("should allow sys_tenant_member only in approved cross tenant scope")
        void shouldAllowSysTenantMemberInApprovedCrossTenantScope() {
            // Given
            String sql = "SELECT * FROM sys_tenant_member WHERE identity_id = 1";
            TenantSqlGuardInterceptor.enterCrossTenantScope(
                    "Enumerate tenant memberships for tenant selector",
                    "BRIX-ARCH-3.0.9-TENANT-SELECTOR",
                    true);

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertEquals(sql, modified);
        }

        @Test
        @DisplayName("CrossTenantAccess must require an approval attribute")
        void crossTenantAccessMustRequireApprovalAttribute() throws NoSuchMethodException {
            assertNotNull(CrossTenantAccess.class.getDeclaredMethod("approval"));
        }

        @Test
        @DisplayName("should not filter flyway_schema_history table")
        void shouldNotFilterFlywayTable() {
            // Given
            String sql = "SELECT * FROM flyway_schema_history";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertFalse(modified.contains("tenant_id = 'tenant-123'"));
        }

        @Test
        @DisplayName("should filter biz_* tables")
        void shouldFilterBusinessTables() {
            // Given
            String sql = "SELECT * FROM biz_customer";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.contains("tenant_id = 'tenant-123'"));
        }
    }

    // =========================================================================
    // No Tenant Context Tests
    // =========================================================================

    @Nested
    @DisplayName("No Tenant Context Tests")
    class NoTenantContextTests {

        @Test
        @DisplayName("should not modify SQL when tenant context is not set")
        void shouldNotModifySqlWhenNoTenantContext() {
            // Given
            TenantContext.clear();
            String sql = "SELECT * FROM biz_case WHERE status = 'OPEN'";

            // When
            String modified = interceptor.inspect(sql);

            // Then - should return original SQL (guard interceptor handles this)
            assertEquals(sql, modified);
        }
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle null SQL gracefully")
        void shouldHandleNullSql() {
            // When
            String modified = interceptor.inspect(null);

            // Then
            assertNull(modified);
        }

        @Test
        @DisplayName("should handle empty SQL gracefully")
        void shouldHandleEmptySql() {
            // When
            String modified = interceptor.inspect("");

            // Then
            assertEquals("", modified);
        }

        @Test
        @DisplayName("should handle blank SQL gracefully")
        void shouldHandleBlankSql() {
            // When
            String modified = interceptor.inspect("   ");

            // Then
            assertEquals("   ", modified);
        }

        @Test
        @DisplayName("should handle SQL with LIMIT clause")
        void shouldHandleSqlWithLimit() {
            // Given
            String sql = "SELECT * FROM biz_case LIMIT 10";

            // When
            String modified = interceptor.inspect(sql);

            // Then - WHERE should be before LIMIT
            int whereIndex = modified.indexOf("WHERE");
            int limitIndex = modified.indexOf("LIMIT");
            assertTrue(whereIndex < limitIndex, "WHERE should come before LIMIT");
        }

        @Test
        @DisplayName("should escape single quotes in tenant ID")
        void shouldEscapeSingleQuotesInTenantId() {
            // Given - tenant ID with single quote (edge case)
            TenantContext.setTenantId("tenant's-test");
            String sql = "SELECT * FROM biz_case";

            // When
            String modified = interceptor.inspect(sql);

            // Then - single quote should be escaped
            assertTrue(modified.contains("tenant''s-test"), 
                "Single quote should be escaped to prevent SQL injection");
        }

        @Test
        @DisplayName("should handle DDL statements without modification")
        void shouldHandleDdlStatements() {
            // Given
            String sql = "CREATE TABLE test (id BIGINT)";

            // When
            String modified = interceptor.inspect(sql);

            // Then - DDL should not be modified
            assertEquals(sql, modified);
        }

        @Test
        @DisplayName("should handle lowercase SQL keywords")
        void shouldHandleLowercaseSqlKeywords() {
            // Given
            String sql = "select * from biz_case where status = 'OPEN'";

            // When
            String modified = interceptor.inspect(sql);

            // Then
            assertTrue(modified.toLowerCase().contains("tenant_id"));
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Counts occurrences of a substring in a string.
     */
    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
