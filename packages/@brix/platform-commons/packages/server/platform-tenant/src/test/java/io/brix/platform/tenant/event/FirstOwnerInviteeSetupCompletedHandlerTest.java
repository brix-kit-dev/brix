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

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.internal.TenantAdministration;
import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.PublisherDecision;
import io.runtime.sdk.plugin.TraceContext;

class FirstOwnerInviteeSetupCompletedHandlerTest {

    @Test
    void resendsThePendingInvitationsOfTheIdentityThatCompletedSetup() {
        TenantAdministration administration = mock(TenantAdministration.class);

        new FirstOwnerInviteeSetupCompletedHandler(() -> administration)
            .handle(delivery(new FirstOwnerInviteeSetupCompletedEvent(5L)));

        verify(administration).sendPendingFirstOwnerInvitationsAfterSetup(5L);
    }

    @Test
    void failsWhileTheOwnerContractIsNotBound() {
        FirstOwnerInviteeSetupCompletedHandler handler = new FirstOwnerInviteeSetupCompletedHandler(() -> null);

        assertThrows(IllegalStateException.class, () -> handler.handle(delivery(new FirstOwnerInviteeSetupCompletedEvent(5L))));
    }

    private static EventInvocation<FirstOwnerInviteeSetupCompletedEvent> delivery(FirstOwnerInviteeSetupCompletedEvent event) {
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
        return new EventInvocation<>(
            new EventEnvelope<>("event-1", "FirstOwnerInviteeSetupCompleted", "1.0.0", Instant.now(), "platform-identity",
                EventScope.PLATFORM, null, "correlation-1", null, traceparent, "5", event, EventReliability.CRITICAL),
            new PublisherDecision("platform-identity", null), new TraceContext(traceparent, null),
            Instant.now().plusSeconds(30), CancellationSignal.none(), 1);
    }
}
