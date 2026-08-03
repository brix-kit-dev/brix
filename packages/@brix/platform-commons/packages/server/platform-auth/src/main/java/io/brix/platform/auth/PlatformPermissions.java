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
 *   <li>{@link io.brix.platform.auth.aspect.PermissionAspect} — evaluates the concrete
 *       permission list carried by the token.</li>
 * </ul>
 *
 * <h3>Role → Permission Mapping</h3>
 * <pre>
 * PLATFORM_SUPER_ADMIN → platform management permissions
 * BOOTSTRAP            → bootstrap status + first-admin creation only
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
    * {@code @RequireRole} guards entirely. This is kept as a break-glass internal
    * permission code and is not emitted into default platform-admin JWTs.
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

    /**
     * Create a new tenant via the platform super-admin console.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint
     * ({@code POST /api/platform/tenants}).
     */
    public static final String TENANT_CREATE = "platform:tenant:create";

    /**
     * Create, resend, or revoke the FIRST_OWNER invitation for a pending tenant.
     *
     * <p>Fine-grained code aligning with platform-admin operational endpoints for
     * {@code /api/platform/tenants/{tenantId}/first-owner-invitations/**}.
     */
    public static final String TENANT_FIRST_OWNER_INVITE = "platform:tenant:first-owner-invite";

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
    * Revoke an existing platform administrator grant.
     *
     * <p>Fine-grained code aligning with SSOT §6 endpoint #5
    * ({@code PATCH /api/platform/admins/{id}/revoke}).
     */
    public static final String ADMIN_REVOKE = "platform:admin:revoke";

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
    * Granted to formal platform super administrators.
     */
    public static final String ADMIN_CHANGE_OWN_PASSWORD = "platform:admin:change-own-password";

    // ========== Bootstrap Setup ==========

    /** Read bootstrap status. */
    public static final String BOOTSTRAP_READ = "platform:bootstrap:read";

    /** Create the first formal platform super administrator during Stage A. */
    public static final String BOOTSTRAP_CREATE_FIRST_ADMIN = "platform:bootstrap:create-first-admin";

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

    /** Access and modify system-level configuration. Restricted to formal super admins. */
    public static final String SYSTEM_CONFIG = "platform:system:config";

    /** Read installation license and tenant quota status. */
    public static final String LICENSE_READ = "platform:license:read";

    /** Perform data recovery and maintenance operations. Restricted to formal super admins. */
    public static final String DATA_RECOVERY = "platform:data:recovery";

    // ========== Factory ==========

    /**
     * Returns the default immutable permission list for a platform admin role.
     *
     * <p>Called at login time to populate the JWT {@code permissions} claim.
     * The {@code adminRole} parameter must match the enum name of
    * {@code io.brix.platform.tenant.enums.PlatformAdminRole}.
     *
     * @param adminRole platform admin role enum name, never {@code null}
     * @return immutable, non-null list of permission codes
     */
    public static List<String> defaultPermissionsFor(String adminRole) {
        if (adminRole == null) {
            return List.of();
        }
        return switch (adminRole) {
            case RoleCode.PLATFORM_SUPER_ADMIN -> List.of(
                    // tenant
                    TENANT_MANAGE, TENANT_VIEW, TENANT_READ, TENANT_CREATE, TENANT_UPDATE_STATUS,
                    TENANT_FIRST_OWNER_INVITE,
                    // admin management
                ADMIN_MANAGE, ADMIN_READ, ADMIN_CREATE, ADMIN_REVOKE,
                    ADMIN_RESET_PASSWORD, ADMIN_CHANGE_OWN_PASSWORD,
                    // audit
                    AUDIT_VIEW, AUDIT_READ,
                        // system
                        SYSTEM_CONFIG, LICENSE_READ, DATA_RECOVERY
            );
            case RoleCode.BOOTSTRAP -> List.of(BOOTSTRAP_READ, BOOTSTRAP_CREATE_FIRST_ADMIN);
            default -> List.of();
        };
    }

    private PlatformPermissions() {
        // utility class — no instances
    }
}
