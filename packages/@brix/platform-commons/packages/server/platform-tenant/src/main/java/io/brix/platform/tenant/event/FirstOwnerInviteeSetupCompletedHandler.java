/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.event;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.brix.platform.tenant.internal.TenantAdministration;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * Sends the pending FIRST_OWNER invitations of an invitee once the identity owner has committed
 * the invitee's setup.
 *
 * <p>Bound for the manifest subscription {@code first-owner-invitee-setup}. The runtime delivers
 * the fact after the identity commit and runs this handler in a platform-tenant unit of work
 * scoped to that identity's pending invitations (multi-tenant §6.4,
 * {@code first-owner-invitee-setup-fanout}); the handler neither opens a transaction nor
 * chooses a tenant.</p>
 */
public final class FirstOwnerInviteeSetupCompletedHandler implements EventHandler<FirstOwnerInviteeSetupCompletedEvent> {

    private final Supplier<TenantAdministration> tenantAdministrationSupplier;

    public FirstOwnerInviteeSetupCompletedHandler(Supplier<TenantAdministration> tenantAdministrationSupplier) {
        this.tenantAdministrationSupplier = Objects.requireNonNull(
            tenantAdministrationSupplier,
            "tenantAdministrationSupplier must not be null");
    }

    @Override
    public CompletionStage<Void> handle(EventInvocation<FirstOwnerInviteeSetupCompletedEvent> invocation) {
        TenantAdministration tenantAdministration = tenantAdministrationSupplier.get();
        if (tenantAdministration == null) {
            throw new IllegalStateException("TenantAdministration is not available before the plugin starts");
        }
        tenantAdministration.sendPendingFirstOwnerInvitationsAfterSetup(invocation.event().payload().identityId());
        return CompletableFuture.completedFuture(null);
    }
}
