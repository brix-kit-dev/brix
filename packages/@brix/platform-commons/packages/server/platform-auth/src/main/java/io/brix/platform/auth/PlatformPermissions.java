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
 * The permissions a platform token carries, by platform role.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C ({@code platform-auth}). Platform permission IDs are defined once, in the versioned catalog
 * {@link PermissionCode} in {@code platform-auth-api} (super-admin §3.3, §4). This class only maps a role to the IDs
 * its token carries, and declares none of its own: the codes outside the catalog it used to hold were retired and are
 * never used again (standard-link plan queue item 18, D158).</p>
 *
 * <h3>Usage</h3>
 * <ul>
 *   <li>JWT {@code permissions} claim and the login response — populated by
 *       {@link #defaultPermissionsFor(String)} at login time.</li>
 *   <li>The Runtime Dispatcher authorizes each entry against that claim, by the permission IDs the
 *       entry's descriptor declares (super-admin §4.1).</li>
 * </ul>
 *
 * <h3>Role → Permission Mapping</h3>
 * <pre>
 * PLATFORM_SUPER_ADMIN → the whole catalog
 * any other value      → nothing
 * </pre>
 * <p>A bootstrap setup session is no platform role: the identity owner gives it its own permissions when it opens the
 * session (super-admin §7).</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class PlatformPermissions {

    /**
     * Role claim of a bootstrap setup session token.
     *
     * <p>Bootstrap is the type and scope of a short-lived setup session, not a platform role
     * (super-admin §3.3), so it is not in {@link RoleCode}. Matches
     * {@code io.brix.platform.identity.enums.PlatformAdminRole.BOOTSTRAP.name()}.
     */
    public static final String BOOTSTRAP_ROLE = "BOOTSTRAP";

    /**
     * Returns the permissions a platform token carries for an admin role: the whole catalog for
     * {@link RoleCode#PLATFORM_SUPER_ADMIN}, nothing for any other value.
     *
     * <p>Called at login time to populate the JWT {@code permissions} claim and the login response.</p>
     *
     * @param adminRole platform admin role, may be {@code null}
     * @return immutable, non-null list of permission IDs, each from {@link PermissionCode}
     */
    public static List<String> defaultPermissionsFor(String adminRole) {
        if (!RoleCode.PLATFORM_SUPER_ADMIN.equals(adminRole)) {
            return List.of();
        }
        return List.of(
                // tenant
                PermissionCode.PLATFORM_TENANT_READ, PermissionCode.PLATFORM_TENANT_CREATE,
                PermissionCode.PLATFORM_TENANT_UPDATE_STATUS, PermissionCode.PLATFORM_TENANT_FIRST_OWNER_INVITE,
                // admin management
                PermissionCode.PLATFORM_ADMIN_READ, PermissionCode.PLATFORM_ADMIN_CREATE,
                PermissionCode.PLATFORM_ADMIN_REVOKE, PermissionCode.PLATFORM_ADMIN_RESET_PASSWORD,
                PermissionCode.PLATFORM_ADMIN_CHANGE_OWN_PASSWORD,
                // audit
                PermissionCode.PLATFORM_AUDIT_READ);
    }

    private PlatformPermissions() {
        // utility class — no instances
    }
}
