/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

import java.util.Optional;

/**
 * Internal contract for platform-scoped tenant administration.
 *
 * <p>This contract is consumed only through Runtime Shell internal-contract
 * resolution. It intentionally returns DTOs and stable errors, never
 * repositories, entities, Spring services, or transaction handles.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public interface TenantAdministration {

    /** Stable contract identifier declared in the Runtime descriptor. */
    String CONTRACT_ID = "brix.internal.tenant.administration";

    /** Exact contract version for v3.0.10 Phase 3. */
    String CONTRACT_VERSION = "1.0.0";

    /**
     * Creates a pending tenant without creating a member, profile, or quota usage.
     *
     * @param command tenant creation command
     * @return tenant view
     */
    TenantAdministrationTenant createPendingTenant(CreatePendingTenantCommand command);

    /**
     * Lists tenants through the tenant Data Owner read projection.
     *
     * @param request pagination, sort, status, and search request
     * @return tenant page view
     */
    PlatformPageView<PlatformTenantView> listTenants(PlatformPageRequest request);

    /**
     * Returns the deployment installation quota and license admission view.
     *
     * @return quota view
     */
    InstallationQuotaView installationQuota();

    /**
     * Returns the latest FIRST_OWNER invitation status for a tenant.
     *
     * @param tenantId tenant identifier
     * @return latest invitation view when one exists
     */
    Optional<FirstOwnerInvitationView> latestFirstOwnerInvitation(Long tenantId);

    /**
     * Creates a FIRST_OWNER invitation and sends the managed notification.
     *
     * @param command invitation command
     * @return invitation view without token or URL
     */
    FirstOwnerInvitationView createFirstOwnerInvitation(CreateFirstOwnerInvitationCommand command);

    /**
     * Revokes and replaces the pending FIRST_OWNER invitation.
     *
     * @param command resend command
     * @return new invitation view without token or URL
     */
    FirstOwnerInvitationView resendFirstOwnerInvitation(ResendFirstOwnerInvitationCommand command);

    /**
     * Revokes a pending FIRST_OWNER invitation.
     *
     * @param command revoke command
     */
    void revokeFirstOwnerInvitation(RevokeFirstOwnerInvitationCommand command);

    /**
     * Accepts a FIRST_OWNER invitation for the authenticated actor identity.
     *
     * @param command acceptance command
     * @return acceptance result
     */
    FirstOwnerAcceptanceResult acceptFirstOwnerInvitation(AcceptFirstOwnerInvitationCommand command);
}
