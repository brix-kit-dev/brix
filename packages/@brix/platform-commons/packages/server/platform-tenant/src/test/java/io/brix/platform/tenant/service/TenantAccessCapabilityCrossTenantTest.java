package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.annotation.CrossTenantAccess;

/**
 * Login and token flows resolve memberships and principalships before any tenant context
 * exists; those identity-scoped reads must be declared read-only cross-tenant access, or the
 * tenant SQL guard rejects them (token issuance and the Dispatcher's authz-version check). The
 * hosted invitation page likewise reads a FIRST_OWNER invitation by its token.
 */
class TenantAccessCapabilityCrossTenantTest {

    @Test
    void identityScopedLookupsAreDeclaredReadOnlyCrossTenantReads() throws Exception {
        for (String method : new String[] {"getActiveMemberships", "getActivePrincipalships"}) {
            assertReadOnlyCrossTenant(TenantAccessCapabilityImpl.class.getMethod(method, Long.class));
        }
        for (String method : new String[] {
                "findMembershipByContextId", "findPrincipalshipByContextId", "findPendingFirstOwnerInvitation"}) {
            assertReadOnlyCrossTenant(TenantAccessCapabilityImpl.class.getMethod(method, String.class));
        }
    }

    private static void assertReadOnlyCrossTenant(java.lang.reflect.Method method) {
        CrossTenantAccess access = method.getAnnotation(CrossTenantAccess.class);
        assertNotNull(access, method.getName() + " must declare @CrossTenantAccess");
        assertTrue(access.readOnly(), method.getName() + " must be read-only");
        assertTrue(!access.approval().isBlank() && !access.reason().isBlank(), method.getName() + " needs a reason and approval");
    }
}
