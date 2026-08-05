/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.sdk.capability;

import java.util.List;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Tenant access context capability.
 *
 * <p>This narrow contract owns tenant memberships, subject principalships, and
 * access-touch updates. It intentionally excludes global identity credential
 * writes.</p>
 *
 * @since 3.2.0
 */
@Since("3.2.0")
public interface TenantAccessCapability {

    List<TenantMembershipRecord> getActiveMemberships(Long identityId);

    List<TenantPrincipalRecord> getActivePrincipalships(Long identityId);

    Optional<TenantMembershipRecord> findMembership(Long identityId, Long tenantId);

    Optional<TenantMembershipRecord> findMembershipByContextId(String contextId);

    Optional<TenantPrincipalRecord> findPrincipalship(Long identityId, Long tenantId);

    Optional<TenantPrincipalRecord> findPrincipalshipByContextId(String contextId);

    void touchMemberAccess(Long memberId);

    void touchPrincipalAccess(Long principalId);

    record TenantMembershipRecord(
            Long memberId,
            Long tenantId,
            String tenantCode,
            String tenantName,
            Long identityId,
            String memberType,
            String status,
            java.time.Instant joinedAt,
            String contextId,
            long authzVersion
    ) {
    }

    record TenantPrincipalRecord(
            Long principalId,
            Long tenantId,
            String tenantCode,
            String tenantName,
            Long identityId,
            String principalType,
            String displayName,
            String status,
            java.time.Instant lastAccessAt,
            String contextId,
            long authzVersion
    ) {
    }
}
