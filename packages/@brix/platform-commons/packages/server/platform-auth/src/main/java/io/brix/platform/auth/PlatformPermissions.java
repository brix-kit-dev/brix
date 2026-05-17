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
package io.brix.platform.auth;

import java.util.List;

/**
 * Platform-level Permission Code Constants.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C ({@code platform-auth}). These codes are written into Platform Admin JWTs
 * and evaluated by {@link io.brix.platform.auth.aspect.PermissionAspect}.
 * They are intentionally separate from tenant-scoped RBAC permission codes stored in the
 * {@code auth_permission} table.
 *
 * <h3>Usage</h3>
 * <ul>
 *   <li>JWT {@code permissions} claim — populated by
 *       {@link #defaultPermissionsFor(String)} at login time.</li>
 *   <li>{@code @RequirePermission} annotations — protect platform management endpoints.</li>
 *   <li>{@link io.brix.platform.auth.aspect.PermissionAspect} — grants capability-based bypass
 *       to any token that carries {@link #BYPASS_PERMISSION_CHECK}.</li>
 * </ul>
 *
 * <h3>Role → Permission Mapping</h3>
 * <pre>
 * SUPER_ADMIN    → bypass + all platform permissions
 * PLATFORM_ADMIN → bypass + tenant manage + audit
 * SUPPORT_ADMIN  → tenant view + audit view
 * AUDITOR        → audit view only
 * </pre>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 * @Frozen — Permission codes are stored in JWTs; changing them requires coordinated token rotation.
 */
public final class PlatformPermissions {

    // ========== Bypass ==========

    /**
     * Wildcard bypass for permission and role checks.
     *
     * <p>Any token carrying this permission bypasses {@code @RequirePermission} and
     * {@code @RequireRole} guards entirely. Granted to SUPER_ADMIN and PLATFORM_ADMIN.
     *
     * <p><b>Security note:</b> This replaces the former role-name identity check
     * {@code isSuperAdmin()} with a capability-based bypass that flows through the JWT
     * and is not contingent on role string matching.
     */
    public static final String BYPASS_PERMISSION_CHECK = "platform:bypass";

    // ========== Tenant Management ==========

    /**
     * Create, update, suspend, or delete tenants (coarse-grained, kept for backward compat).
     *
     * <p>Prefer fine-grained codes {@link #TENANT_READ} and {@link #TENANT_UPDATE_STATUS} for
     * new endpoint annotations. This code is retained so that older tokens that carry it
     * continue to satisfy {@code @RequirePermission(TENANT_MANAGE)} guards.
     */
    public static final String TENANT_MANAGE = "platform:tenant:manage";

    /** Read tenant details (without modification) — coarse alias, kept for backward compat. */
    public static final String TENANT_VIEW = "platform:tenant:view";

    /**
     * Read platform-scoped tenant list and tenant details.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #9 ({@code GET /api/platform/tenants}).
     */
    public static final String TENANT_READ = "platform:tenant:read";

    /**
     * Change tenant lifecycle status (ACTIVE → SUSPENDED → ACTIVE).
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #10
     * ({@code PATCH /api/platform/tenants/{id}/status}).
     */
    public static final String TENANT_UPDATE_STATUS = "platform:tenant:update-status";

    // ========== Platform Admin Management ==========

    /** Create or manage platform administrator accounts (coarse-grained, kept for backward compat). */
    public static final String ADMIN_MANAGE = "platform:admin:manage";

    /**
     * Read platform admin list and individual admin details.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #3 ({@code GET /api/platform/admins}).
     */
    public static final String ADMIN_READ = "platform:admin:read";

    /**
     * Create a new platform administrator account.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #4 ({@code POST /api/platform/admins}).
     */
    public static final String ADMIN_CREATE = "platform:admin:create";

    /**
     * Disable an existing platform administrator account.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #5
     * ({@code PATCH /api/platform/admins/{id}/disable}).
     */
    public static final String ADMIN_DISABLE = "platform:admin:disable";

    /**
     * Reset the password of another platform administrator.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #6
     * ({@code POST /api/platform/admins/{id}/reset-password}).
     */
    public static final String ADMIN_RESET_PASSWORD = "platform:admin:reset-password";

    /**
     * Change the caller's own password.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #7
     * ({@code POST /api/platform/admins/me/change-password}).
     * Granted to all platform admin roles (SUPER_ADMIN, PLATFORM_ADMIN, SUPPORT_ADMIN, AUDITOR).
     */
    public static final String ADMIN_CHANGE_OWN_PASSWORD = "platform:admin:change-own-password";

    // ========== Audit ==========

    /** Read platform audit logs and compliance reports (coarse alias, kept for backward compat). */
    public static final String AUDIT_VIEW = "platform:audit:view";

    /**
     * Read platform audit logs with pagination and filtering.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #8 ({@code GET /api/platform/audit-logs}).
     */
    public static final String AUDIT_READ = "platform:audit:read";

    // ========== System ==========

    /** Access and modify system-level configuration. Restricted to SUPER_ADMIN. */
    public static final String SYSTEM_CONFIG = "platform:system:config";

    /** Perform data recovery and maintenance operations. Restricted to SUPER_ADMIN. */
    public static final String DATA_RECOVERY = "platform:data:recovery";

    // ========== Factory ==========

    /**
     * Returns the default immutable permission list for a platform admin role.
     *
     * <p>Called at login time to populate the JWT {@code permissions} claim.
     * The {@code adminRole} parameter must match the enum name of
     * {@code io.brix.platform.tenant.enums.PlatformAdminRole} (e.g. {@code "SUPER_ADMIN"}).
     *
     * @param adminRole platform admin role enum name, never {@code null}
     * @return immutable, non-null list of permission codes
     */
    public static List<String> defaultPermissionsFor(String adminRole) {
        if (adminRole == null) {
            return List.of();
        }
        // Fine-grained codes are returned for each role so that JWT tokens carry
        // the exact permission set required by @RequirePermission guards on every endpoint.
        // Coarse codes (TENANT_MANAGE, ADMIN_MANAGE, AUDIT_VIEW) are kept for backward
        // compat with older token-holders that predate the fine-grained expansion.
        return switch (adminRole) {
            case "SUPER_ADMIN" -> List.of(
                    BYPASS_PERMISSION_CHECK,
                    // tenant
                    TENANT_MANAGE, TENANT_VIEW, TENANT_READ, TENANT_UPDATE_STATUS,
                    // admin management
                    ADMIN_MANAGE, ADMIN_READ, ADMIN_CREATE, ADMIN_DISABLE,
                    ADMIN_RESET_PASSWORD, ADMIN_CHANGE_OWN_PASSWORD,
                    // audit
                    AUDIT_VIEW, AUDIT_READ,
                    // system
                    SYSTEM_CONFIG, DATA_RECOVERY
            );
            case "PLATFORM_ADMIN" -> List.of(
                    BYPASS_PERMISSION_CHECK,
                    // tenant
                    TENANT_MANAGE, TENANT_VIEW, TENANT_READ, TENANT_UPDATE_STATUS,
                    // admin management (can create/disable/reset, not system config)
                    ADMIN_MANAGE, ADMIN_READ, ADMIN_CREATE, ADMIN_DISABLE,
                    ADMIN_RESET_PASSWORD, ADMIN_CHANGE_OWN_PASSWORD,
                    // audit
                    AUDIT_VIEW, AUDIT_READ
            );
            case "SUPPORT_ADMIN" -> List.of(
                    TENANT_VIEW, TENANT_READ,
                    ADMIN_READ,
                    ADMIN_CHANGE_OWN_PASSWORD,
                    AUDIT_VIEW, AUDIT_READ
            );
            case "AUDITOR" -> List.of(
                    TENANT_READ,
                    ADMIN_READ,
                    ADMIN_CHANGE_OWN_PASSWORD,
                    AUDIT_VIEW, AUDIT_READ
            );
            default -> List.of();
        };
    }

    private PlatformPermissions() {
        // utility class — no instances
    }
}
