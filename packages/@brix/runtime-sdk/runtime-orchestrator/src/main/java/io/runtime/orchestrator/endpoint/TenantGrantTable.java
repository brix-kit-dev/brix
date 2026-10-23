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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.runtime.sdk.plugin.AccessScope;

/**
 * Immutable grant table of the built-in tenant roles.
 *
 * <p>The Runtime builds one table per Host entry generation from the
 * {@code permissions[].grants} declarations of the selected plugins, before any
 * route is published, and rejects invalid declarations fail-fast. The
 * Dispatcher expands the verified role type of a tenant actor or subject token
 * through it on every request; permission lists carried by tenant tokens are
 * never trusted.</p>
 *
 * <p>Rules (multi-tenant 5.1 §9.8): permission IDs are unique in the Host and
 * every permission an endpoint references is declared; a permission is granted
 * to Actor roles or to Subject roles, never both; Subject roles only receive
 * {@link AccessScope#SELF}; a {@code tenant-actor} endpoint never references a
 * permission granted to Subject roles and a {@code tenant-subject} endpoint
 * never references one granted to Actor roles.</p>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public final class TenantGrantTable {

    private static final TenantGrantTable EMPTY = new TenantGrantTable(Map.of());

    private final Map<TenantRole, Map<String, AccessScope>> grantsByRole;

    private TenantGrantTable(Map<TenantRole, Map<String, AccessScope>> grantsByRole) {
        this.grantsByRole = grantsByRole;
    }

    /**
     * Returns a table that grants nothing to any tenant role.
     *
     * @return empty table
     */
    public static TenantGrantTable empty() {
        return EMPTY;
    }

    /**
     * Validates the declarations of the selected plugins and builds the table.
     *
     * @param permissions permission catalog entries of every selected plugin
     * @param endpoints access policies of every selected plugin endpoint
     * @return immutable table
     * @throws EndpointDispatchException when a declaration violates a grant rule
     */
    public static TenantGrantTable build(
            Collection<PermissionDeclaration> permissions,
            Collection<EndpointPolicyDeclaration> endpoints) {
        Objects.requireNonNull(permissions, "permissions must not be null");
        Objects.requireNonNull(endpoints, "endpoints must not be null");

        List<PermissionDeclaration> ordered = new ArrayList<>(permissions);
        ordered.sort(Comparator.comparing(PermissionDeclaration::ownerId)
            .thenComparing(PermissionDeclaration::permissionId));

        Map<String, String> owners = new HashMap<>();
        Map<String, RoleClass> grantedClasses = new HashMap<>();
        Map<TenantRole, Map<String, AccessScope>> grantsByRole = new LinkedHashMap<>();
        for (PermissionDeclaration declaration : ordered) {
            String permission = declaration.permissionId();
            String previousOwner = owners.putIfAbsent(permission, declaration.ownerId());
            if (previousOwner != null) {
                throw invalid(previousOwner.equals(declaration.ownerId())
                    ? "Permission '" + permission + "' is declared twice by '" + previousOwner + "'"
                    : "Permission '" + permission + "' is declared by both '" + previousOwner
                        + "' and '" + declaration.ownerId() + "'");
            }
            Set<TenantRole> grantedRoles = EnumSet.noneOf(TenantRole.class);
            for (Grant grant : declaration.grants()) {
                TenantRole role = TenantRole.parse(grant.role()).orElseThrow(() -> invalid(
                    declaration, "grants unknown tenant role '" + grant.role() + "'"));
                AccessScope scope = parseScope(grant.scope()).orElseThrow(() -> invalid(
                    declaration, "grants unknown scope '" + grant.scope() + "' to " + role));
                if (!grantedRoles.add(role)) {
                    throw invalid(declaration, "grants role " + role + " more than once");
                }
                RoleClass previousClass = grantedClasses.putIfAbsent(permission, role.roleClass());
                if (previousClass != null && previousClass != role.roleClass()) {
                    throw invalid(declaration, "grants both Actor and Subject roles");
                }
                if (role.roleClass() == RoleClass.SUBJECT && scope != AccessScope.SELF) {
                    throw invalid(declaration, "grants Subject role " + role + " scope " + scope
                        + "; Subject roles may only receive SELF");
                }
                grantsByRole.computeIfAbsent(role, ignored -> new LinkedHashMap<>()).put(permission, scope);
            }
        }

        for (EndpointPolicyDeclaration endpoint : endpoints) {
            Optional<String> audience = endpoint.accessPolicy().tokenPolicy().tenantTokenRole();
            for (String permission : endpoint.accessPolicy().permissions()) {
                if (!owners.containsKey(permission)) {
                    throw invalid("Endpoint '" + endpoint.ownerId() + "/" + endpoint.endpointId()
                        + "' references undeclared permission '" + permission + "'");
                }
                RoleClass granted = grantedClasses.get(permission);
                if (audience.isPresent() && granted != null && !granted.tokenRole().equals(audience.get())) {
                    throw invalid("Endpoint '" + endpoint.ownerId() + "/" + endpoint.endpointId()
                        + "' accepts only " + audience.get() + " tokens but permission '" + permission
                        + "' is granted to " + granted.tokenRole() + " roles");
                }
            }
        }

        Map<TenantRole, Map<String, AccessScope>> frozen = new LinkedHashMap<>();
        grantsByRole.forEach((role, grants) -> frozen.put(role, Map.copyOf(grants)));
        return new TenantGrantTable(Map.copyOf(frozen));
    }

    /**
     * Returns the effective permissions of a verified tenant caller.
     *
     * @param tokenRole verified token role, {@code actor} or {@code subject}
     * @param tenantRoleType verified member type of an actor or principal type of a subject
     * @return permission IDs mapped to their data scope; empty when the role is
     *         unknown or does not belong to the token role
     */
    public Map<String, AccessScope> effectivePermissions(String tokenRole, String tenantRoleType) {
        if (tokenRole == null) {
            return Map.of();
        }
        return TenantRole.parse(tenantRoleType)
            .filter(role -> role.roleClass().tokenRole().equals(tokenRole))
            .map(role -> grantsByRole.getOrDefault(role, Map.of()))
            .orElse(Map.of());
    }

    private static Optional<AccessScope> parseScope(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (AccessScope scope : AccessScope.values()) {
            if (scope.name().equals(value.trim())) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }

    private static EndpointDispatchException invalid(PermissionDeclaration declaration, String problem) {
        return invalid("Permission '" + declaration.permissionId() + "' of '" + declaration.ownerId()
            + "' " + problem);
    }

    private static EndpointDispatchException invalid(String message) {
        return new EndpointDispatchException("Invalid tenant grant declaration: " + message);
    }

    /**
     * Class of the tenant token a built-in role belongs to.
     */
    public enum RoleClass {
        /** Tenant staff, carried by actor tokens. */
        ACTOR("actor"),
        /** End users of a tenant, carried by subject tokens. */
        SUBJECT("subject");

        private final String tokenRole;

        RoleClass(String tokenRole) {
            this.tokenRole = tokenRole;
        }

        /**
         * Returns the token role of this class.
         *
         * @return {@code actor} or {@code subject}
         */
        public String tokenRole() {
            return tokenRole;
        }
    }

    /**
     * Built-in tenant roles: the member types and principal types of the tenant model.
     */
    public enum TenantRole {
        /** Tenant owner. */
        OWNER(RoleClass.ACTOR),
        /** Tenant administrator. */
        ADMIN(RoleClass.ACTOR),
        /** Tenant staff member. */
        MEMBER(RoleClass.ACTOR),
        /** End user served by the tenant. */
        CUSTOMER(RoleClass.SUBJECT),
        /** Restricted visitor. */
        GUEST(RoleClass.SUBJECT);

        private final RoleClass roleClass;

        TenantRole(RoleClass roleClass) {
            this.roleClass = roleClass;
        }

        /**
         * Returns the token class this role belongs to.
         *
         * @return role class
         */
        public RoleClass roleClass() {
            return roleClass;
        }

        static Optional<TenantRole> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (TenantRole role : values()) {
                if (role.name().equals(normalized)) {
                    return Optional.of(role);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Declared default grant of a permission.
     *
     * @param role declared role value
     * @param scope declared scope value
     */
    public record Grant(String role, String scope) {
    }

    /**
     * Permission catalog entry declared by a plugin manifest.
     *
     * @param ownerId declaring plugin
     * @param permissionId permission ID
     * @param grants declared default grants
     */
    public record PermissionDeclaration(String ownerId, String permissionId, List<Grant> grants) {

        /**
         * Validates and defensively copies the declaration.
         */
        public PermissionDeclaration {
            ownerId = requireText(ownerId, "ownerId");
            permissionId = requireText(permissionId, "permissionId");
            grants = List.copyOf(Objects.requireNonNull(grants, "grants must not be null"));
        }
    }

    /**
     * Access policy of one plugin endpoint.
     *
     * @param ownerId declaring plugin
     * @param endpointId endpoint ID
     * @param accessPolicy declared access policy
     */
    public record EndpointPolicyDeclaration(String ownerId, String endpointId, EndpointAccessPolicy accessPolicy) {

        /**
         * Validates the declaration.
         */
        public EndpointPolicyDeclaration {
            ownerId = requireText(ownerId, "ownerId");
            endpointId = requireText(endpointId, "endpointId");
            accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy must not be null");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
