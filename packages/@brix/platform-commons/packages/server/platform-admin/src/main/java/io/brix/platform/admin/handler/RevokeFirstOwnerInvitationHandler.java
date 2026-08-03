/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.tenant.internal.RevokeFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler that asks the tenant Owner to revoke a pending invite.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class RevokeFirstOwnerInvitationHandler implements EndpointHandler<EndpointInvocation<Void>, Void> {

    private final TenantAdministration tenantAdministration;

    public RevokeFirstOwnerInvitationHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
    }

    @Override
    public Void handle(EndpointInvocation<Void> invocation) {
        Long actorId = PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        Long tenantId = PlatformOperationalInvocationSupport.requirePathLong(invocation, "tenantId");
        Long invitationId = PlatformOperationalInvocationSupport.requirePathLong(invocation, "invitationId");
        try {
            tenantAdministration.revokeFirstOwnerInvitation(
                new RevokeFirstOwnerInvitationCommand(
                    tenantId,
                    invitationId,
                    PlatformOperationalInvocationSupport.platformOperatorRef(actorId)));
        } catch (TenantAdministrationException ex) {
            throw PlatformEndpointErrors.tenantAdministration(ex);
        }
        return null;
    }
}
