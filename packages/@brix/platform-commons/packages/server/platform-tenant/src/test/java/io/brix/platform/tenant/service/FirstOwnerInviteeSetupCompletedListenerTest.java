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

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

class FirstOwnerInviteeSetupCompletedListenerTest {

    @Test
    void setupCompletedEventDelegatesToTenantOwnerInvitationDelivery() {
        FirstOwnerInvitationService invitationService = mock(FirstOwnerInvitationService.class);
        FirstOwnerInviteeSetupCompletedListener listener =
            new FirstOwnerInviteeSetupCompletedListener(invitationService);

        listener.onFirstOwnerInviteeSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(77L));

        verify(invitationService).sendPendingInvitationsAfterSetup(77L);
    }

    @Test
    void setupCompletedDeliveryUsesTenantOwnerLocalTransaction() throws NoSuchMethodException {
        Method method = FirstOwnerInvitationService.class
            .getMethod("sendPendingInvitationsAfterSetup", Long.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }
}
