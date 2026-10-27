/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.brix.platform.tenant.support.RecordingUnitOfWork;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

class FirstOwnerInviteeSetupCompletedListenerTest {

    @Test
    void setupCompletedEventDelegatesToTenantOwnerInvitationDelivery() {
        FirstOwnerInvitationService invitationService = mock(FirstOwnerInvitationService.class);
        FirstOwnerInviteeSetupCompletedListener listener =
            new FirstOwnerInviteeSetupCompletedListener(invitationService, new RecordingUnitOfWork());

        listener.onFirstOwnerInviteeSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(77L));

        verify(invitationService).sendPendingInvitationsAfterSetup(77L);
    }

    @Test
    void setupCompletedDeliveryUsesTenantOwnerLocalTransaction() {
        FirstOwnerInvitationService invitationService = mock(FirstOwnerInvitationService.class);
        RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();
        FirstOwnerInviteeSetupCompletedListener listener =
            new FirstOwnerInviteeSetupCompletedListener(invitationService, unitOfWork);

        listener.onFirstOwnerInviteeSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(77L));

        // Delivery runs in a platform-tenant unit of work of its own, separate from identity's.
        assertEquals(List.of("required"), unitOfWork.modes());
    }

    @Test
    void setupCompletedDeliveryWaitsForTheIdentityCommit() throws NoSuchMethodException {
        Method method = FirstOwnerInviteeSetupCompletedListener.class
            .getMethod("onFirstOwnerInviteeSetupCompleted", FirstOwnerInviteeSetupCompletedEvent.class);

        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
    }
}
