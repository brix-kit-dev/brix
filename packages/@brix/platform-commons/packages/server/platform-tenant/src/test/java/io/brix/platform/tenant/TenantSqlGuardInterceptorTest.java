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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.core.TenantWhitelist;
import io.brix.platform.tenant.exception.TenantSqlViolationException;
import io.brix.platform.tenant.interceptor.TenantSqlGuardInterceptor;

class TenantSqlGuardInterceptorTest {

    private final TenantSqlGuardInterceptor guard = new TenantSqlGuardInterceptor(true, true);

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        if (TenantSqlGuardInterceptor.isInCrossTenantScope()) {
            TenantSqlGuardInterceptor.exitCrossTenantScope();
        }
    }

    @Test
    void tenantMembershipTablesAreNotGloballyWhitelisted() {
        assertFalse(TenantWhitelist.isWhitelisted("sys_tenant_member"));
        assertFalse(TenantWhitelist.isWhitelisted("sys_tenant_principal"));
    }

    @Test
    void sysPrefixIsNotBlanketWhitelisted() {
        Set<String> expectedSysWhitelist = Set.of(
                "sys_tenant",
                "sys_identity",
                "sys_platform_admin",
                "sys_platform_config",
                "sys_installation_quota",
                "sys_platform_audit_log");

        Set<String> actualSysWhitelist = TenantWhitelist.getWhitelistedTables().stream()
                .filter(table -> table.startsWith("sys_"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(expectedSysWhitelist, actualSysWhitelist);
        assertFalse(TenantWhitelist.isWhitelisted("sys_tenant_config"));
        assertFalse(TenantWhitelist.isWhitelisted("sys_config"));
        assertFalse(TenantWhitelist.isWhitelisted("sys_feature_flags"));
    }

    @Test
    void tenantMembershipQueryWithoutTenantContextFails() {
        assertThrows(TenantSqlViolationException.class,
                () -> guard.inspect("SELECT * FROM sys_tenant_member WHERE identity_id = 1"));
    }

    @Test
    void tenantMembershipQueryInApprovedCrossTenantScopePasses() {
        TenantSqlGuardInterceptor.enterCrossTenantScope(
                "Enumerate memberships by identity for tenant selector",
                "BRIX-ARCH-3.0.9-TENANT-SELECTOR",
                true);

        assertDoesNotThrow(() -> guard.inspect("SELECT * FROM sys_tenant_member WHERE identity_id = 1"));
    }

    @Test
    void readOnlyCrossTenantScopeRejectsWrites() {
        TenantSqlGuardInterceptor.enterCrossTenantScope(
                "Read platform audit data",
                "BRIX-ARCH-3.0.9-READONLY",
                true);

        assertThrows(TenantSqlViolationException.class,
                () -> guard.inspect("UPDATE sys_tenant_member SET status = 'DISABLED' WHERE id = 1"));
    }

    @Test
    void authPrefixIsNotBlanketWhitelisted() {
        assertThrows(TenantSqlViolationException.class,
                () -> guard.inspect("SELECT * FROM auth_user WHERE id = 1"));
    }

    @Test
    void authRefreshTokenIsExplicitlyWhitelisted() {
        assertDoesNotThrow(() -> guard.inspect("SELECT * FROM auth_refresh_token WHERE identity_id = 1"));
    }
}
