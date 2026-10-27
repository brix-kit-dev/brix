/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.service;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * Tenant Owner listener for FIRST_OWNER invitee setup completion.
 *
 * <p>platform-identity publishes the event inside its own unit of work, so the listener waits for that
 * commit: a setup that rolls back must not send the invitation. It runs outside any Runtime entry point
 * and therefore opens the platform-tenant unit of work itself.</p>
 */
public final class FirstOwnerInviteeSetupCompletedListener {

    private final FirstOwnerInvitationService firstOwnerInvitationService;
    private final UnitOfWork unitOfWork;

    public FirstOwnerInviteeSetupCompletedListener(
            FirstOwnerInvitationService firstOwnerInvitationService,
            UnitOfWork unitOfWork) {
        this.firstOwnerInvitationService = firstOwnerInvitationService;
        this.unitOfWork = unitOfWork;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFirstOwnerInviteeSetupCompleted(FirstOwnerInviteeSetupCompletedEvent event) {
        unitOfWork.required(() -> {
            firstOwnerInvitationService.sendPendingInvitationsAfterSetup(event.identityId());
            return null;
        });
    }
}
