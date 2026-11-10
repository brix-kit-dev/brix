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

import io.brix.platform.auth.api.PermissionCode;
import io.brix.platform.auth.api.RoleCode;

/**
 * Platform-level Permission Code Constants.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C ({@code platform-auth}). The permission IDs endpoints declare are the versioned catalog
 * {@link PermissionCode} in {@code platform-auth-api} (super-admin 4.0.1 §4). This class keeps the codes
 * outside that catalog and maps a platform admin role to the permissions its JWT carries.
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

    // ========== Bootstrap Role Claim ==========

    /**
     * Role claim of a bootstrap setup session token.
     *
     * <p>Bootstrap is the type and scope of a short-lived setup session, not a platform role
     * (super-admin §3.3), so it is not in {@link RoleCode}. Matches
     * {@code io.brix.platform.identity.enums.PlatformAdminRole.BOOTSTRAP.name()}.
     */
    public static final String BOOTSTRAP_ROLE = "BOOTSTRAP";

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
     * <p>Prefer fine-grained codes {@link PermissionCode#PLATFORM_TENANT_READ} and
     * {@link PermissionCode#PLATFORM_TENANT_UPDATE_STATUS} for new endpoint annotations. This code is
     * retained so that older tokens that carry it continue to satisfy
     * {@code @RequirePermission(TENANT_MANAGE)} guards.
     */
    public static final String TENANT_MANAGE = "platform:tenant:manage";

    /** Read tenant details (without modification) — coarse alias, kept for backward compat. */
    public static final String TENANT_VIEW = "platform:tenant:view";

    // ========== Platform Admin Management ==========

    /** Create or manage platform administrator accounts (coarse-grained, kept for backward compat). */
    public static final String ADMIN_MANAGE = "platform:admin:manage";

    // ========== Bootstrap Setup ==========

    /** Read bootstrap status. */
    public static final String BOOTSTRAP_READ = "platform:bootstrap:read";

    /** Create the first formal platform super administrator during Stage A. */
    public static final String BOOTSTRAP_CREATE_FIRST_ADMIN = "platform:bootstrap:create-first-admin";

    // ========== Audit ==========

    /** Read platform audit logs and compliance reports (coarse alias, kept for backward compat). */
    public static final String AUDIT_VIEW = "platform:audit:view";

    // ========== System ==========

    /** Access and modify system-level configuration. Restricted to formal super admins. */
    public static final String SYSTEM_CONFIG = "platform:system:config";

    /**
     * Read installation license and tenant quota status.
     *
     * <p>{@code GET /api/platform/license/quota} declares it, although the super-admin 4.0.1 catalog
     * does not list it.
     */
    public static final String LICENSE_READ = "platform:license:read";

    /** Perform data recovery and maintenance operations. Restricted to formal super admins. */
    public static final String DATA_RECOVERY = "platform:data:recovery";

    // ========== Factory ==========

    /**
     * Returns the default immutable permission list for a platform admin role.
     *
     * <p>Called at login time to populate the JWT {@code permissions} claim.
     * The {@code adminRole} parameter must match the enum name of
     * {@code io.brix.platform.identity.enums.PlatformAdminRole}.
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
                    TENANT_MANAGE, TENANT_VIEW, PermissionCode.PLATFORM_TENANT_READ,
                    PermissionCode.PLATFORM_TENANT_CREATE, PermissionCode.PLATFORM_TENANT_UPDATE_STATUS,
                    PermissionCode.PLATFORM_TENANT_FIRST_OWNER_INVITE,
                    // admin management
                    ADMIN_MANAGE, PermissionCode.PLATFORM_ADMIN_READ, PermissionCode.PLATFORM_ADMIN_CREATE,
                    PermissionCode.PLATFORM_ADMIN_REVOKE, PermissionCode.PLATFORM_ADMIN_RESET_PASSWORD,
                    PermissionCode.PLATFORM_ADMIN_CHANGE_OWN_PASSWORD,
                    // audit
                    AUDIT_VIEW, PermissionCode.PLATFORM_AUDIT_READ,
                    // system
                    SYSTEM_CONFIG, LICENSE_READ, DATA_RECOVERY
            );
            case BOOTSTRAP_ROLE -> List.of(BOOTSTRAP_READ, BOOTSTRAP_CREATE_FIRST_ADMIN);
            default -> List.of();
        };
    }

    private PlatformPermissions() {
        // utility class — no instances
    }
}
