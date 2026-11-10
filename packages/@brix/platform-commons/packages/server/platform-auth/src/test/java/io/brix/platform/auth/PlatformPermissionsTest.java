package io.brix.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.brix.platform.auth.api.PermissionCode;
import io.brix.platform.auth.api.RoleCode;

class PlatformPermissionsTest {

    @Test
    void platformSuperAdminReceivesFirstOwnerInvitationPermission() {
        var permissions = PlatformPermissions.defaultPermissionsFor(RoleCode.PLATFORM_SUPER_ADMIN);

        assertTrue(permissions.contains(PermissionCode.PLATFORM_TENANT_FIRST_OWNER_INVITE));
        assertFalse(permissions.contains(PlatformPermissions.BYPASS_PERMISSION_CHECK));
    }

    /**
     * What a platform token carries, written out: moving the catalog into platform-auth-api (standard-link plan
     * queue item 4) changes no token. Seven of the super administrator's codes are outside the catalog.
     */
    @Test
    void tokensCarryTheseCodesInThisOrder() {
        assertEquals(List.of(
                "platform:tenant:manage", "platform:tenant:view", "platform:tenant:read", "platform:tenant:create",
                "platform:tenant:update-status", "platform:tenant:first-owner-invite",
                "platform:admin:manage", "platform:admin:read", "platform:admin:create", "platform:admin:revoke",
                "platform:admin:reset-password", "platform:admin:change-own-password",
                "platform:audit:view", "platform:audit:read",
                "platform:system:config", "platform:license:read", "platform:data:recovery"),
            PlatformPermissions.defaultPermissionsFor(RoleCode.PLATFORM_SUPER_ADMIN));
        assertEquals(List.of("platform:bootstrap:read", "platform:bootstrap:create-first-admin"),
            PlatformPermissions.defaultPermissionsFor(PlatformPermissions.BOOTSTRAP_ROLE));
        assertEquals(List.of(), PlatformPermissions.defaultPermissionsFor("TENANT_ADMIN"));
        assertEquals(List.of(), PlatformPermissions.defaultPermissionsFor(null));
    }
}
