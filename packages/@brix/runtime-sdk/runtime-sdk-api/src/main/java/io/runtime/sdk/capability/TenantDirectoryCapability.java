/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.runtime.sdk.capability;

import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Tenant directory capability contract.
 *
 * <p>This capability resolves Actor/Subject references only inside the current
 * authenticated tenant context. It intentionally does not expose arbitrary
 * tenant-id lookup methods, so plugins cannot bypass tenant isolation.</p>
 *
 * @since 3.2.2
 */
@Since("3.2.2")
public interface TenantDirectoryCapability {

    /**
     * Reference kind used by the current-tenant lookup methods.
     */
    enum RefKind {
        /** B-side tenant member. */
        MEMBER,
        /** C-side tenant principal. */
        PRINCIPAL
    }

    /**
     * Finds a reference in the current tenant context.
     *
     * @param refKind reference kind
     * @param refId member ID or principal ID
     * @return reference view when it exists in the current tenant
     */
    Optional<TenantRef> findInCurrentTenant(RefKind refKind, Long refId);

    /**
     * Requires that a reference belongs to the current tenant context.
     *
     * @param refKind reference kind
     * @param refId member ID or principal ID
     * @return reference view
     * @throws SecurityException when the reference is absent or belongs to another tenant
     */
    default TenantRef requireSameTenant(RefKind refKind, Long refId) {
        return findInCurrentTenant(refKind, refId)
                .orElseThrow(() -> new SecurityException("Reference is not in the current tenant"));
    }

    /**
     * Finds a reference by its immutable context ID.
     *
     * <p>This method is for platform authentication/runtime infrastructure. It
     * must still return only active references and must not be exposed as a
     * plugin data query shortcut.</p>
     *
     * @param contextId immutable context ID
     * @return reference view when present
     */
    Optional<TenantRef> findByContextId(String contextId);

    /**
     * Read-only tenant reference view.
     *
     * @param tenantId owning tenant ID
     * @param kind reference kind
     * @param refId member ID or principal ID
     * @param contextId immutable context ID
     * @param status lifecycle status
     * @param roleType member type or principal type
     * @param identityId global identity ID
     * @param authzVersion current authorization version
     */
    record TenantRef(Long tenantId, RefKind kind, Long refId, String contextId,
                     String status, String roleType, Long identityId, long authzVersion) {}
}
