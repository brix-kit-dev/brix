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
package io.runtime.sdk.plugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Authorization result the Runtime Dispatcher hands to an endpoint handler.
 *
 * <p>The Dispatcher builds it before the handler runs. {@link #permissions()}
 * holds the declared endpoint permissions the caller was found to hold. For
 * tenant actor and subject tokens they come from the Runtime grant table and
 * {@link #scopes()} maps each of them to its {@link AccessScope}; platform
 * tokens carry no tenant data scope. A handler of a permission-protected
 * endpoint applies the scope of the permission it serves in its own repository
 * queries and denies when that scope is missing. Handlers never compute
 * permissions or parse roles themselves.</p>
 *
 * @param permissions declared endpoint permissions held by the caller
 * @param scopes data scope of each held tenant permission
 * @param tenantRoleType verified tenant role type ({@code OWNER}, {@code ADMIN},
 *        {@code MEMBER}, {@code CUSTOMER} or {@code GUEST}) of a tenant caller
 * @param tenantRefId verified member id of a tenant actor or principal id of a
 *        tenant subject; this is the caller that {@link AccessScope#SELF} refers to
 * @param effectivePermissions every permission the verified tenant role holds in the
 *        current context, with its scope, from the same grant table (multi-tenant 5.1
 *        {@code BRX-TENANT-AUTHZ-009}); it describes the caller's session, for example to a
 *        BFF, and is never an authorization result for the current endpoint. Empty for
 *        callers without a tenant role (L2A Minor addition, root CAP-020).
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public record AccessDecision(
        Set<String> permissions,
        Map<String, AccessScope> scopes,
        Optional<String> tenantRoleType,
        Optional<String> tenantRefId,
        Map<String, AccessScope> effectivePermissions) {

    private static final AccessDecision NONE =
        new AccessDecision(Set.of(), Map.of(), Optional.empty(), Optional.empty(), Map.of());

    /**
     * Validates and defensively copies the decision.
     */
    public AccessDecision {
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        scopes = Map.copyOf(Objects.requireNonNull(scopes, "scopes must not be null"));
        tenantRoleType = tenantRoleType != null ? tenantRoleType : Optional.empty();
        tenantRefId = tenantRefId != null ? tenantRefId : Optional.empty();
        effectivePermissions = effectivePermissions != null ? Map.copyOf(effectivePermissions) : Map.of();
        if (!permissions.containsAll(scopes.keySet())) {
            throw new IllegalArgumentException("scopes may only cover held permissions");
        }
    }

    /**
     * Creates a decision without the caller's effective permission set.
     *
     * @param permissions declared endpoint permissions held by the caller
     * @param scopes data scope of each held tenant permission
     * @param tenantRoleType verified tenant role type of a tenant caller
     * @param tenantRefId verified member or principal id of a tenant caller
     */
    public AccessDecision(Set<String> permissions, Map<String, AccessScope> scopes,
                          Optional<String> tenantRoleType, Optional<String> tenantRefId) {
        this(permissions, scopes, tenantRoleType, tenantRefId, Map.of());
    }

    /**
     * Returns the decision of an endpoint that declares no permission and has
     * no verified tenant caller.
     *
     * @return empty decision
     */
    public static AccessDecision none() {
        return NONE;
    }

    /**
     * Returns the widest data scope among the held tenant permissions.
     *
     * <p>Sufficient for an endpoint that declares one permission. An endpoint
     * that combines several permissions applies {@link #scopeOf(String)} to the
     * objects each permission covers.</p>
     *
     * @return widest scope; empty when no tenant permission is held
     */
    public Optional<AccessScope> scope() {
        return scopes.values().stream().reduce(AccessScope::widest);
    }

    /**
     * Returns the data scope of one held permission.
     *
     * @param permission permission ID
     * @return scope; empty when the permission is not held or carries no tenant scope
     */
    public Optional<AccessScope> scopeOf(String permission) {
        return Optional.ofNullable(scopes.get(permission));
    }
}
