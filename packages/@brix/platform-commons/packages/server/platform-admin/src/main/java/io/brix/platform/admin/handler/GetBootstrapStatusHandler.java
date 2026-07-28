/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.admin.dto.BootstrapStatusResponse;
import io.brix.platform.tenant.internal.PlatformBootstrapAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/** Handler for querying the public bootstrap status. */
public final class GetBootstrapStatusHandler
        implements EndpointHandler<EndpointInvocation<Void>, BootstrapStatusResponse> {

    private final PlatformBootstrapAdministration bootstrapAdministration;

    public GetBootstrapStatusHandler(OperationalContext context) {
        this.bootstrapAdministration = context.requireInternalContract(PlatformBootstrapAdministration.class);
    }

    @Override
    public BootstrapStatusResponse handle(EndpointInvocation<Void> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw new IllegalArgumentException("bootstrap endpoints forbid tenant context");
        });
        var status = bootstrapAdministration.status();
        return new BootstrapStatusResponse(status.open(), status.setupCodeExpiresAt(), status.completedAt());
    }
}
