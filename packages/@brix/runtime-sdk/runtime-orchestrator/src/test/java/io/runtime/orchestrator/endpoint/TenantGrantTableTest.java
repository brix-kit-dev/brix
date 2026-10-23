/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.endpoint.TenantGrantTable.EndpointPolicyDeclaration;
import io.runtime.orchestrator.endpoint.TenantGrantTable.Grant;
import io.runtime.orchestrator.endpoint.TenantGrantTable.PermissionDeclaration;
import io.runtime.sdk.plugin.AccessScope;

/**
 * Grant table rules of multi-tenant 5.1 §9.8 (BRX-TENANT-AUTHZ-001 to 004).
 */
class TenantGrantTableTest {

    private static final String ORDER_READ = "example:order:read";
    private static final String MY_ORDER_READ = "example:my-order:read";

    @Test
    void expandsVerifiedTenantRolesIntoScopedPermissions() {
        TenantGrantTable table = TenantGrantTable.build(List.of(
            permission("plugin-a", ORDER_READ,
                grant("OWNER", "TENANT"), grant("ADMIN", "TENANT"), grant("MEMBER", "SELF")),
            permission("plugin-a", MY_ORDER_READ, grant("CUSTOMER", "SELF"))),
            List.of(
                endpoint("plugin-a", "order.list", "tenant-actor", ORDER_READ),
                endpoint("plugin-a", "my-order.list", "tenant-subject", MY_ORDER_READ)));

        assertEquals(Map.of(ORDER_READ, AccessScope.TENANT), table.effectivePermissions("actor", "ADMIN"));
        assertEquals(Map.of(ORDER_READ, AccessScope.SELF), table.effectivePermissions("actor", "MEMBER"));
        assertEquals(Map.of(MY_ORDER_READ, AccessScope.SELF), table.effectivePermissions("subject", "CUSTOMER"));
        assertTrue(table.effectivePermissions("subject", "GUEST").isEmpty(), "ungranted GUEST gets nothing");
    }

    @Test
    void roleTypeOfTheOtherTokenClassOrUnknownRoleGrantsNothing() {
        TenantGrantTable table = TenantGrantTable.build(List.of(
            permission("plugin-a", ORDER_READ, grant("OWNER", "TENANT"))), List.of());

        assertTrue(table.effectivePermissions("subject", "OWNER").isEmpty());
        assertTrue(table.effectivePermissions("actor", "CUSTOMER").isEmpty());
        assertTrue(table.effectivePermissions("actor", "SUPERUSER").isEmpty());
        assertTrue(table.effectivePermissions("actor", null).isEmpty());
        assertTrue(table.effectivePermissions(null, "OWNER").isEmpty());
        assertTrue(TenantGrantTable.empty().effectivePermissions("actor", "OWNER").isEmpty());
    }

    @Test
    void subjectRolesOnlyReceiveSelf() {
        assertInvalid("Subject roles may only receive SELF",
            List.of(permission("plugin-a", MY_ORDER_READ, grant("CUSTOMER", "TENANT"))), List.of());
    }

    @Test
    void permissionIsGrantedToOneRoleClassOnly() {
        assertInvalid("grants both Actor and Subject roles",
            List.of(permission("plugin-a", ORDER_READ, grant("ADMIN", "TENANT"), grant("CUSTOMER", "SELF"))),
            List.of());
    }

    @Test
    void subjectEndpointMustNotReferenceActorGrantedPermission() {
        assertInvalid("accepts only subject tokens",
            List.of(permission("plugin-a", ORDER_READ, grant("ADMIN", "TENANT"))),
            List.of(endpoint("plugin-a", "my-order.list", "tenant-subject", ORDER_READ)));
    }

    @Test
    void actorEndpointMustNotReferenceSubjectGrantedPermission() {
        assertInvalid("accepts only actor tokens",
            List.of(permission("plugin-a", MY_ORDER_READ, grant("CUSTOMER", "SELF"))),
            List.of(endpoint("plugin-a", "order.list", "tenant-actor", MY_ORDER_READ)));
    }

    @Test
    void endpointMustNotReferenceUndeclaredPermission() {
        assertInvalid("references undeclared permission 'example:order:read'",
            List.of(), List.of(endpoint("plugin-a", "order.list", "tenant-actor", ORDER_READ)));
    }

    @Test
    void permissionWithoutGrantsMayBeReferencedAndGrantsNothing() {
        TenantGrantTable table = TenantGrantTable.build(
            List.of(permission("plugin-a", ORDER_READ)),
            List.of(endpoint("plugin-a", "order.list", "tenant-actor", ORDER_READ)));

        assertTrue(table.effectivePermissions("actor", "OWNER").isEmpty());
    }

    @Test
    void permissionIdIsUniqueInTheHost() {
        assertInvalid("declared by both 'plugin-a' and 'plugin-b'",
            List.of(permission("plugin-a", ORDER_READ), permission("plugin-b", ORDER_READ)), List.of());
        assertInvalid("declared twice by 'plugin-a'",
            List.of(permission("plugin-a", ORDER_READ), permission("plugin-a", ORDER_READ)), List.of());
    }

    @Test
    void grantsMustNameKnownRolesAndScopesOnce() {
        assertInvalid("unknown tenant role 'SUPERUSER'",
            List.of(permission("plugin-a", ORDER_READ, grant("SUPERUSER", "TENANT"))), List.of());
        assertInvalid("unknown scope 'DEPARTMENT'",
            List.of(permission("plugin-a", ORDER_READ, grant("ADMIN", "DEPARTMENT"))), List.of());
        assertInvalid("grants role ADMIN more than once",
            List.of(permission("plugin-a", ORDER_READ, grant("ADMIN", "TENANT"), grant("ADMIN", "SELF"))),
            List.of());
    }

    private static void assertInvalid(
            String expectedMessagePart,
            List<PermissionDeclaration> permissions,
            List<EndpointPolicyDeclaration> endpoints) {
        EndpointDispatchException invalid = assertThrows(
            EndpointDispatchException.class,
            () -> TenantGrantTable.build(permissions, endpoints));
        assertTrue(invalid.getMessage().contains(expectedMessagePart),
            () -> "unexpected message: " + invalid.getMessage());
    }

    private static PermissionDeclaration permission(String owner, String id, Grant... grants) {
        return new PermissionDeclaration(owner, id, List.of(grants));
    }

    private static Grant grant(String role, String scope) {
        return new Grant(role, scope);
    }

    private static EndpointPolicyDeclaration endpoint(
            String owner,
            String endpointId,
            String tokenPolicy,
            String permission) {
        return new EndpointPolicyDeclaration(owner, endpointId, EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", tokenPolicy),
            Map.of("permissions", List.of(permission)),
            Map.of("mode", "required")));
    }
}
