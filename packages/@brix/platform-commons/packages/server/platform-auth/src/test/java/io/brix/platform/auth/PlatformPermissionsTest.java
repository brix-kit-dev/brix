package io.brix.platform.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformPermissionsTest {

    @Test
    void platformSuperAdminReceivesFirstOwnerInvitationPermission() {
        var permissions = PlatformPermissions.defaultPermissionsFor(RoleCode.PLATFORM_SUPER_ADMIN);

        assertTrue(permissions.contains(PlatformPermissions.TENANT_FIRST_OWNER_INVITE));
        assertFalse(permissions.contains(PlatformPermissions.BYPASS_PERMISSION_CHECK));
    }
}
