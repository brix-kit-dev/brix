/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * Operational handler that asks the tenant Owner to resend FIRST_OWNER invite.
 *
 * <p>The caller names a key for each resend it means, and the runtime keeps the record with the owner's write: a
 * repeated call gets the first answer, and no further invitation or e-mail goes out, while a new key is a new resend
 * (root BRX-RS-MANIFEST-018; idempotency design §5.6).</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class ResendFirstOwnerInvitationHandler
        implements TypedEndpointHandler<ResendFirstOwnerInvitationRequest, FirstOwnerInvitationDto> {

    private final TenantAdministration tenantAdministration;
    private final PlatformOperationAudit audit;

    public ResendFirstOwnerInvitationHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
        this.audit = new PlatformOperationAudit(context);
    }

    @Override
    public CompletionStage<FirstOwnerInvitationDto> handle(
            EndpointInvocation<ResendFirstOwnerInvitationRequest> invocation) {
        Long actorId = PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        Long tenantId = PlatformOperationalInvocationSupport.requirePathLong(invocation, "tenantId");
        ResendFirstOwnerInvitationRequest request = invocation.body();
        return CompletableFuture.completedFuture(audit.run(invocation, actorId,
            PlatformAuditActions.FIRST_OWNER_INVITATION_RESENT,
            new PlatformOperationAudit.Target("INVITATION", null, tenantId),
            resent -> new PlatformOperationAudit.Target("INVITATION", resent.invitationId(), tenantId),
            () -> {
                try {
                    return FirstOwnerInvitationDto.from(tenantAdministration.resendFirstOwnerInvitation(
                        new ResendFirstOwnerInvitationCommand(
                            tenantId,
                            PlatformOperationalInvocationSupport.platformOperatorRef(actorId),
                            request.locale())));
                } catch (TenantAdministrationException ex) {
                    throw PlatformEndpointErrors.tenantAdministration(ex);
                }
            }));
    }
}
