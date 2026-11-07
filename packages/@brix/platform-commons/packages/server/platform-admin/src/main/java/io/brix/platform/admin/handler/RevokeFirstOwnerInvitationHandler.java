/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.tenant.internal.RevokeFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * Operational handler that asks the tenant Owner to revoke a pending invite.
 *
 * <p>The caller names a key for each revoke it means, and the runtime keeps the record with the owner's write: a
 * repeated call gets the first answer, a 204, instead of hearing that the invitation it revoked is no longer revokable
 * (root BRX-RS-MANIFEST-018; idempotency design §5.6; standard-link plan point 10b-6).</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class RevokeFirstOwnerInvitationHandler
        implements TypedEndpointHandler<RevokeFirstOwnerInvitationRequest, Void> {

    private final TenantAdministration tenantAdministration;
    private final PlatformOperationAudit audit;

    public RevokeFirstOwnerInvitationHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
        this.audit = new PlatformOperationAudit(context);
    }

    @Override
    public CompletionStage<Void> handle(EndpointInvocation<RevokeFirstOwnerInvitationRequest> invocation) {
        Long actorId = PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        RevokeFirstOwnerInvitationRequest request = invocation.body();
        PlatformOperationAudit.Target invitation =
            new PlatformOperationAudit.Target("INVITATION", request.invitationId(), request.tenantId());
        audit.<Void>run(invocation, actorId, PlatformAuditActions.FIRST_OWNER_INVITATION_REVOKED,
            invitation,
            revoked -> invitation,
            () -> {
                try {
                    tenantAdministration.revokeFirstOwnerInvitation(
                        new RevokeFirstOwnerInvitationCommand(
                            request.tenantId(),
                            request.invitationId(),
                            PlatformOperationalInvocationSupport.platformOperatorRef(actorId)));
                } catch (TenantAdministrationException ex) {
                    throw PlatformEndpointErrors.tenantAdministration(ex);
                }
                return null;
            });
        return CompletableFuture.completedFuture(null);
    }
}
