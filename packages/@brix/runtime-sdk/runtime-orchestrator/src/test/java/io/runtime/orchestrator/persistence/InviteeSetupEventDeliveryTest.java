/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

class InviteeSetupEventDeliveryTest {

    private static final String OWNER = "platform-tenant";

    private final List<TenantScope> handled = new ArrayList<>();
    private final RecordingPersistence persistence = new RecordingPersistence();
    private final List<BoundEventHandler> subscribers = new ArrayList<>();
    private final FirstOwnerInviteeIdentitySetupCapability identities =
        mock(FirstOwnerInviteeIdentitySetupCapability.class);

    @Test
    void theSetupFanoutIsKeyedByTheEmailOfTheIdentityThatCompletedSetup() {
        subscribe("first-owner-invitee-setup-fanout");
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");

        delivery().onSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(5L));

        TenantScope fanout = TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, "owner@example.com");
        assertEquals(List.of(fanout), handled);
        assertEquals(List.of("required"), persistence.modes());
        assertEquals(List.of(fanout), persistence.scopes());
        assertEquals(TenantScope.none(), TenantScopes.requested(), "the scope must not outlive the delivery");
    }

    @Test
    void subscriptionsWithoutARegisteredScopeReachNoTenantData() {
        subscribe("");

        delivery().onSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(5L));

        assertEquals(List.of(TenantScope.none()), handled);
    }

    @Test
    void subscriptionsCannotChooseAKeyTheRuntimeMustBind() {
        subscribe("login-tenant-discovery");

        delivery().onSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(5L));

        assertEquals(List.of(TenantScope.none()), handled);
    }

    private void subscribe(String scopePolicyRef) {
        PluginRuntimeDescriptor.EventSubscription subscription = new PluginRuntimeDescriptor.EventSubscription(
            "first-owner-invitee-setup", InviteeSetupEventDelivery.EVENT_TYPE, "^1.0", "first-owner-invitee-setup.v1",
            "no-retry", "invitation-reissue", scopePolicyRef);
        subscribers.add(new BoundEventHandler(OWNER, subscription, event -> handled.add(TenantScopes.requested())));
    }

    @SuppressWarnings("unchecked")
    private InviteeSetupEventDelivery delivery() {
        ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(identities);
        return new InviteeSetupEventDelivery(
            eventType -> InviteeSetupEventDelivery.EVENT_TYPE.equals(eventType) ? subscribers : List.of(),
            owner -> OWNER.equals(owner) ? Optional.of(persistence) : Optional.empty(), provider);
    }
}
