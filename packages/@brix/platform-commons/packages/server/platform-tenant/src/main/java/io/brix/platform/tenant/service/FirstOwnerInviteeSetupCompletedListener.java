/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.service;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * Tenant Owner listener for FIRST_OWNER invitee setup completion.
 */
public final class FirstOwnerInviteeSetupCompletedListener {

    private final FirstOwnerInvitationService firstOwnerInvitationService;

    public FirstOwnerInviteeSetupCompletedListener(FirstOwnerInvitationService firstOwnerInvitationService) {
        this.firstOwnerInvitationService = firstOwnerInvitationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFirstOwnerInviteeSetupCompleted(FirstOwnerInviteeSetupCompletedEvent event) {
        firstOwnerInvitationService.sendPendingInvitationsAfterSetup(event.identityId());
    }
}
