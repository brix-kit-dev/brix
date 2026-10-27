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

    /**
     * Lists the identity's subject principalships across every tenant.
     *
     * @deprecated since 3.3.0, removed in 4.0.0. Subject discovery is bound to the tenant of the
     *     brand client (multi-tenant §9.2); use {@link #findPrincipalship(Long, Long)}. Disabled
     *     early under RFC-006: calls fail with {@code persistence.tenant_scope_required}.
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    List<TenantPrincipalRecord> getActivePrincipalships(Long identityId);

    Optional<TenantMembershipRecord> findMembership(Long identityId, Long tenantId);

    /**
     * Resolves an Actor context by its id alone.
     *
     * @deprecated since 3.3.0, removed in 4.0.0. A context is always resolved with the tenant
     *     that comes with it; use {@link #findMembershipByContext(Long, String)}. Resolving a context across tenants is
     *     not a registered cross-tenant access (multi-tenant §6.4), so calls fail with
     *     {@code persistence.tenant_scope_required} (RFC-007).
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    Optional<TenantMembershipRecord> findMembershipByContextId(String contextId);

    /**
     * Resolves an Actor context inside its tenant: the token's {@code tid}, or the tenant the
     * login transaction offered with the context.
     *
     * @param tenantId tenant the context belongs to
     * @param contextId stable context id
     * @return the active membership, when the context belongs to that tenant
     * @since 3.3.0
     */
    @Since("3.3.0")
    default Optional<TenantMembershipRecord> findMembershipByContext(Long tenantId, String contextId) {
        return findMembershipByContextId(contextId).filter(membership -> membership.tenantId().equals(tenantId));
    }

    Optional<TenantPrincipalRecord> findPrincipalship(Long identityId, Long tenantId);

    /**
     * Resolves a Subject context by its id alone.
     *
     * @deprecated since 3.3.0, removed in 4.0.0. A context is always resolved with the tenant
     *     that comes with it; use {@link #findPrincipalshipByContext(Long, String)}. Resolving a context across tenants is
     *     not a registered cross-tenant access (multi-tenant §6.4), so calls fail with
     *     {@code persistence.tenant_scope_required} (RFC-007).
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    Optional<TenantPrincipalRecord> findPrincipalshipByContextId(String contextId);

    /**
     * Resolves a Subject context inside its tenant, the token's {@code tid}.
     *
     * @param tenantId tenant the context belongs to
     * @param contextId stable context id
     * @return the active principalship, when the context belongs to that tenant
     * @since 3.3.0
     */
    @Since("3.3.0")
    default Optional<TenantPrincipalRecord> findPrincipalshipByContext(Long tenantId, String contextId) {
        return findPrincipalshipByContextId(contextId).filter(principal -> principal.tenantId().equals(tenantId));
    }

    /**
     * Records an Actor access by member id alone.
     *
     * @deprecated since 3.3.0, removed in 4.0.0. Use {@link #touchMemberAccess(Long, Long)}.
     *     Disabled early under RFC-006: calls fail with {@code persistence.tenant_scope_required}.
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    void touchMemberAccess(Long memberId);

    /**
     * Records an Actor access inside the member's tenant.
     *
     * @param tenantId tenant of the member
     * @param memberId member id
     * @since 3.3.0
     */
    @Since("3.3.0")
    default void touchMemberAccess(Long tenantId, Long memberId) {
        touchMemberAccess(memberId);
    }

    /**
     * Records a Subject access by principal id alone.
     *
     * @deprecated since 3.3.0, removed in 4.0.0. Use {@link #touchPrincipalAccess(Long, Long)}.
     *     Disabled early under RFC-006: calls fail with {@code persistence.tenant_scope_required}.
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    void touchPrincipalAccess(Long principalId);

    /**
     * Records a Subject access inside the principal's tenant.
     *
     * @param tenantId tenant of the principal
     * @param principalId principal id
     * @since 3.3.0
     */
    @Since("3.3.0")
    default void touchPrincipalAccess(Long tenantId, Long principalId) {
        touchPrincipalAccess(principalId);
    }

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
