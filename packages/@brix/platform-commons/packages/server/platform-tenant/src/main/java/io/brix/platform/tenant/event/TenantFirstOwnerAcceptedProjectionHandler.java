/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.event;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.platform.tenant.service.FirstOwnerProjectionWriter;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * Writes the first-owner projection of a tenant once its {@code TenantFirstOwnerAccepted} fact is
 * delivered.
 *
 * <p>Bound for the manifest subscription {@code tenant-first-owner-projection}. The runtime records
 * {@code (handlerId, eventId)} in {@code platform_tenant_inbox} and calls this handler in the same
 * platform-tenant unit of work, scoped to the envelope's tenant, so a redelivered event never
 * reaches it twice (P4 design §7.3). The projection keeps the envelope's event id.</p>
 */
public final class TenantFirstOwnerAcceptedProjectionHandler implements EventHandler<TenantFirstOwnerAcceptedPayload> {

    private final FirstOwnerProjectionWriter projections;

    public TenantFirstOwnerAcceptedProjectionHandler(FirstOwnerProjectionWriter projections) {
        this.projections = Objects.requireNonNull(projections, "projections must not be null");
    }

    @Override
    public CompletionStage<Void> handle(EventInvocation<TenantFirstOwnerAcceptedPayload> invocation) {
        projections.write(invocation.event().eventId(), invocation.event().payload());
        return CompletableFuture.completedFuture(null);
    }
}
