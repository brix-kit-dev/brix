/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.admin.dto.PlatformSetupValidateResponse;
import io.brix.platform.tenant.internal.PlatformIdentityAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/** Handler for validating a setup token without consuming it. */
public final class ValidatePlatformSetupHandler
        implements EndpointHandler<EndpointInvocation<Void>, PlatformSetupValidateResponse> {

    private final PlatformIdentityAdministration identityAdministration;

    public ValidatePlatformSetupHandler(OperationalContext context) {
        this.identityAdministration = context.requireInternalContract(PlatformIdentityAdministration.class);
    }

    @Override
    public PlatformSetupValidateResponse handle(EndpointInvocation<Void> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw new IllegalArgumentException("setup endpoints forbid tenant context");
        });
        var view = identityAdministration.validateSetupToken(
                PlatformOperationalInvocationSupport.requiredQueryParameter(invocation, "token"));
        return new PlatformSetupValidateResponse(
                view.valid(),
                view.identityId(),
                view.email(),
                view.username(),
                view.purpose(),
                view.expiresAt());
    }
}
