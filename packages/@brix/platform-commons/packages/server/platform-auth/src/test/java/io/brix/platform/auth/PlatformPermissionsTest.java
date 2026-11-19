package io.brix.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import io.brix.platform.auth.api.PermissionCode;
import io.brix.platform.auth.api.RoleCode;

class PlatformPermissionsTest {

    /**
     * Super-admin BRX-SA-AUTH-006: a super administrator's token carries the catalog, every ID once and nothing else,
     * so a code outside {@link PermissionCode} never reaches a token again (standard-link plan queue item 18, D158,
     * D160). The catalog is read by reflection: an ID added to it and not granted here fails too.
     */
    @Test
    void theSuperAdministratorCarriesExactlyTheCatalog() throws IllegalAccessException {
        List<String> permissions = PlatformPermissions.defaultPermissionsFor(RoleCode.PLATFORM_SUPER_ADMIN);

        assertEquals(new HashSet<>(stringConstants(PermissionCode.class).values()), Set.copyOf(permissions));
        assertEquals(Set.copyOf(permissions).size(), permissions.size(), "each ID once");
    }

    /** What a platform token carries, written out in its order, so that a change shows in review. */
    @Test
    void tokensCarryTheseCodesInThisOrder() {
        assertEquals(List.of(
                "platform:tenant:read", "platform:tenant:create", "platform:tenant:update-status",
                "platform:tenant:first-owner-invite",
                "platform:admin:read", "platform:admin:create", "platform:admin:revoke", "platform:admin:reset-password",
                "platform:admin:change-own-password",
                "platform:audit:read"),
            PlatformPermissions.defaultPermissionsFor(RoleCode.PLATFORM_SUPER_ADMIN));
        // A bootstrap setup session gets its permissions from the identity owner when it is opened, not from a role.
        assertEquals(List.of(), PlatformPermissions.defaultPermissionsFor(PlatformPermissions.BOOTSTRAP_ROLE));
        assertEquals(List.of(), PlatformPermissions.defaultPermissionsFor("TENANT_ADMIN"));
        assertEquals(List.of(), PlatformPermissions.defaultPermissionsFor(null));
    }

    /**
     * The class declares no permission ID of its own, only the bootstrap token's role claim: the codes it used to hold
     * outside the catalog are retired (D158), and a new one would be a second source of platform permission IDs.
     */
    @Test
    void declaresNoPermissionIdOfItsOwn() throws IllegalAccessException {
        assertEquals(Map.of("BOOTSTRAP_ROLE", "BOOTSTRAP"), stringConstants(PlatformPermissions.class));
    }

    /** A class's static String constants by name (a coverage agent's synthetic field aside). */
    private static Map<String, String> stringConstants(Class<?> type) throws IllegalAccessException {
        Map<String, String> constants = new TreeMap<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic() && Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true);
                constants.put(field.getName(), (String) field.get(null));
            }
        }
        return constants;
    }
}
