/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.internal.TenantAdministration;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

class FirstOwnerInviteeSetupCompletedHandlerTest {

    @Test
    void resendsThePendingInvitationsOfTheIdentityThatCompletedSetup() {
        TenantAdministration administration = mock(TenantAdministration.class);

        new FirstOwnerInviteeSetupCompletedHandler(() -> administration)
            .handle(new FirstOwnerInviteeSetupCompletedEvent(5L));

        verify(administration).sendPendingFirstOwnerInvitationsAfterSetup(5L);
    }

    @Test
    void failsWhileTheOwnerContractIsNotBound() {
        FirstOwnerInviteeSetupCompletedHandler handler = new FirstOwnerInviteeSetupCompletedHandler(() -> null);

        assertThrows(IllegalStateException.class, () -> handler.handle(new FirstOwnerInviteeSetupCompletedEvent(5L)));
    }
}
