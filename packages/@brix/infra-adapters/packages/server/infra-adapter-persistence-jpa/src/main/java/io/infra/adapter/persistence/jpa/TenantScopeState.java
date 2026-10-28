/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.persistence.jpa;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantIsolationRegister;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * The tenant scope one owner transaction is bound to, and the tenants it has reached so far.
 *
 * <p>A {@link TenantScope.Tenants} scope is fixed. A policy scope starts with no tenant: only its
 * keyed table can be read, through the key, and every keyed row loaded binds its tenant. A
 * platform operation without a target binds the one tenant it creates. Tenants are only ever
 * added, never removed or chosen by the owner's code.</p>
 */
final class TenantScopeState {

    private static final Logger audit = LoggerFactory.getLogger("brix.tenant.isolation.audit");

    private final TenantScope scope;
    private final OwnerTenantTables tables;
    private final Set<Long> bound = new LinkedHashSet<>();
    private int version;
    private int syncedVersion = -1;

    TenantScopeState(TenantScope requested, OwnerTenantTables tables) {
        this.tables = Objects.requireNonNull(tables, "tables must not be null");
        this.scope = ownScope(Objects.requireNonNull(requested, "scope must not be null"), tables.ownerId());
    }

    /**
     * A registered policy belongs to its owner (multi-tenant §6.4). Another owner's unit of work that
     * starts while the policy is in force, such as a capability the policy's handler calls, reaches
     * no tenant data, only its global tables.
     */
    private static TenantScope ownScope(TenantScope requested, String ownerId) {
        return requested instanceof TenantScope.Policy && !TenantAccessPolicy.OWNER.equals(ownerId)
            ? TenantScope.none()
            : requested;
    }

    TenantScope scope() {
        return scope;
    }

    OwnerTenantTables tables() {
        return tables;
    }

    /** The tenants statements and writes may reach now. */
    Set<Long> tenants() {
        if (scope instanceof TenantScope.Tenants tenants) {
            return tenants.tenantIds();
        }
        return Set.copyOf(bound);
    }

    /** The policy, when the scope is one. */
    Optional<TenantAccessPolicy> policy() {
        return scope instanceof TenantScope.Policy policy ? Optional.of(policy.policy()) : Optional.empty();
    }

    /**
     * The registered cross-tenant access this transaction runs under (multi-tenant §6.4): the
     * policy id, or {@code platform-operation}. Empty for tenant scopes and for no scope.
     */
    Optional<String> crossTenantAccess() {
        if (scope instanceof TenantScope.Policy policy) {
            return Optional.of(policy.policy().id());
        }
        return scope instanceof TenantScope.PlatformOperation ? Optional.of("platform-operation") : Optional.empty();
    }

    /**
     * Returns whether a statement may read the tenant table.
     *
     * @param table tenant table
     * @return {@code true} when readable in this scope
     */
    boolean mayRead(String table) {
        if (scope instanceof TenantScope.Tenants) {
            return true;
        }
        if (scope instanceof TenantScope.Policy policy) {
            TenantAccessPolicy p = policy.policy();
            return p.keyedTable().equals(table) || (!bound.isEmpty() && p.reads(table));
        }
        return !bound.isEmpty();
    }

    /**
     * Returns whether a statement or an entity write may change the tenant table.
     *
     * @param table tenant table
     * @return {@code true} when writable in this scope
     */
    boolean mayWrite(String table) {
        if (scope instanceof TenantScope.Tenants) {
            return true;
        }
        if (scope instanceof TenantScope.Policy policy) {
            return !bound.isEmpty() && policy.policy().writes(table);
        }
        return !bound.isEmpty();
    }

    /**
     * Returns the key column a statement must match on the table while no tenant is bound.
     *
     * @param table tenant table
     * @return the key column of the policy's keyed table
     */
    Optional<String> requiredKeyColumn(String table) {
        if (scope instanceof TenantScope.Policy policy && bound.isEmpty()
                && policy.policy().keyedTable().equals(table)) {
            return Optional.of(policy.policy().keyColumn());
        }
        return Optional.empty();
    }

    /**
     * Binds the tenant of a keyed row a policy loaded.
     *
     * @param table table of the row
     * @param tenantId tenant of the row
     */
    void bindLoaded(String table, Long tenantId) {
        if (!(scope instanceof TenantScope.Policy policy) || !policy.policy().keyedTable().equals(table)
                || tenantId == null || bound.contains(tenantId)) {
            return;
        }
        if (bound.size() >= policy.policy().maxTenants()) {
            throw TenantViolations.report(
                violation("policy " + policy.policy().id() + " reached more tenants than it may bind"),
                TenantViolations.Reason.TENANT_BINDING, "table=" + table);
        }
        bound.add(tenantId);
        version++;
        TenantScopes.reportBound(tenantId);
        audit.info("event=tenant.policy.bound owner={} policy={} tenant={}",
            tables.ownerId(), policy.policy().id(), tenantId);
    }

    /**
     * Binds the tenant a platform operation without a target creates.
     *
     * @param tenantId id of the new tenant registry row
     */
    void bindCreated(Long tenantId) {
        if (!(scope instanceof TenantScope.PlatformOperation) || tenantId == null) {
            return;
        }
        if (!bound.isEmpty() && !bound.contains(tenantId)) {
            throw TenantViolations.report(violation("a platform operation may create one tenant at a time"),
                TenantViolations.Reason.TENANT_BINDING, "table=" + TenantIsolationRegister.TENANT_REGISTRY_TABLE);
        }
        if (bound.add(tenantId)) {
            version++;
            TenantScopes.reportBound(tenantId);
        }
    }

    /**
     * Makes the session's filters match the scope: the tenant filter over the reachable tenants
     * and, for a policy whose key the runtime bound, the key filter.
     *
     * @param session session of the owner transaction
     */
    void syncFilters(Session session) {
        if (syncedVersion == version) {
            return;
        }
        Set<Long> tenants = tenants();
        if (tenants.isEmpty()) {
            session.disableFilter(TenantFilters.TENANT_FILTER);
        } else {
            session.enableFilter(TenantFilters.TENANT_FILTER)
                .setParameterList(TenantFilters.TENANT_PARAMETER, tenants);
        }
        for (TenantAccessPolicy policy : TenantAccessPolicy.values()) {
            if (!policy.keyBoundByRuntime()) {
                continue;
            }
            if (scope instanceof TenantScope.Policy requested && requested.policy() == policy) {
                session.enableFilter(policy.keyFilterName()).setParameter(TenantFilters.KEY_PARAMETER, requested.key());
            } else {
                session.disableFilter(policy.keyFilterName());
            }
        }
        syncedVersion = version;
    }

    /** Describes the scope for diagnostics, without tenant ids or keys. */
    Map<String, String> safeParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("owner", tables.ownerId());
        String kind;
        if (scope instanceof TenantScope.Tenants) {
            kind = "tenant";
        } else if (scope instanceof TenantScope.Policy policy) {
            kind = "policy:" + policy.policy().id();
        } else if (scope instanceof TenantScope.PlatformOperation) {
            kind = "platform-operation";
        } else {
            kind = "none";
        }
        parameters.put("scope", kind);
        return parameters;
    }

    PersistenceCapabilityException required(String detail) {
        return new PersistenceCapabilityException(
            PersistenceCapabilityException.Code.TENANT_SCOPE_REQUIRED,
            safeParameters(),
            new IllegalStateException(detail));
    }

    PersistenceCapabilityException violation(String detail) {
        return new PersistenceCapabilityException(
            PersistenceCapabilityException.Code.TENANT_SCOPE_VIOLATION,
            safeParameters(),
            new IllegalStateException(detail));
    }
}
