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

    /**
     * Finds the FIRST_OWNER invitation of an invitation link for the hosted invitation page of
     * the identity protocol surface (identity-auth {@code BRX-IA-ONBOARD-002}). Only an invitation
     * that can still be accepted is found. Accepting it remains the tenant Owner's acceptance
     * entry (multi-tenant 5.1 §5.7, {@code BRX-TENANT-ENTRY-005}).
     *
     * <p>Owners that do not manage invitations keep this default.</p>
     *
     * @param invitationToken raw token of the invitation link
     * @return the invitation, while it is pending and its tenant awaits activation
     * @since 3.2.0
     */
    default Optional<FirstOwnerInvitationRecord> findPendingFirstOwnerInvitation(String invitationToken) {
        return Optional.empty();
    }

    /**
     * A FIRST_OWNER invitation as the hosted invitation page shows it; it carries no token
     * material.
     *
     * @param tenantId tenant to be taken over
     * @param tenantName its name
     * @param inviteeEmail invited email, which the accepting identity must have
     * @param expiresAt when the invitation expires
     * @since 3.2.0
     */
    record FirstOwnerInvitationRecord(
            Long tenantId,
            String tenantName,
            String inviteeEmail,
            java.time.Instant expiresAt
    ) {
    }

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
