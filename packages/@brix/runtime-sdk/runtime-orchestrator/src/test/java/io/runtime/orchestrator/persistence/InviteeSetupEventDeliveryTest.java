/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.error.PolicyAuditException;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.EventHandler;

class InviteeSetupEventDeliveryTest {

    private static final String OWNER = "platform-tenant";

    private final List<TenantScope> handled = new ArrayList<>();
    private final RecordingPersistence persistence = new RecordingPersistence();
    private final List<BoundEventHandler> subscribers = new ArrayList<>();
    private final FirstOwnerInviteeIdentitySetupCapability identities =
        mock(FirstOwnerInviteeIdentitySetupCapability.class);
    private final RecordingPolicyAuditWriter writer = new RecordingPolicyAuditWriter().watching(persistence::active);

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

    @Test
    void theResendIsAuditedInsideTheOwnersUnitOfWorkWithTheTenantsItReached() {
        subscribe("first-owner-invitee-setup-fanout", event -> {
            TenantScopes.reportBound(910001L);
            TenantScopes.reportBound(910002L);
        });
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");

        delivery().onSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(5L));

        PolicyUseRecord record = writer.records().get(0);
        assertEquals(List.of(true), writer.insideUnitOfWork(), "the record must be written before the owner commits");
        assertEquals("FIRST_OWNER_INVITEE_SETUP_FANOUT", record.action());
        assertEquals(5L, record.actorIdentityId());
        assertEquals(List.of(910001L, 910002L), record.affectedTenants());
        assertEquals(Map.of("subscription", "first-owner-invitee-setup"), record.context());
        assertEquals(true, record.success());
    }

    @Test
    void whenTheRecordCannotBeWrittenTheResendFailsWithoutAFailureRecord() {
        subscribe("first-owner-invitee-setup-fanout");
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");
        PolicyAuditException refused = new PolicyAuditException(Map.of("policy", "first-owner-invitee-setup-fanout"));
        writer.failingWith(refused);

        assertSame(refused, assertThrows(PolicyAuditException.class,
            () -> delivery().onSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(5L))));
        assertEquals(List.of(true), writer.insideUnitOfWork(), "one attempt, inside the unit of work");
    }

    @Test
    void aFailingResendLeavesAFailureRecordAfterTheRollback() {
        IllegalStateException broken = new IllegalStateException("mail relay down");
        subscribe("first-owner-invitee-setup-fanout", event -> {
            TenantScopes.reportBound(910001L);
            throw broken;
        });
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");

        assertSame(broken, assertThrows(IllegalStateException.class,
            () -> delivery().onSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(5L))));
        PolicyUseRecord record = writer.records().get(0);
        assertEquals(List.of(false), writer.insideUnitOfWork(), "the failure record is written after the rollback");
        assertEquals(false, record.success());
        assertEquals("policy.handler_failed", record.errorCode());
        assertEquals(List.of(910001L), record.affectedTenants());
    }

    @Test
    void subscriptionsWithoutAPolicyWriteNoAuditRecord() {
        subscribe("");

        delivery().onSetupCompleted(new FirstOwnerInviteeSetupCompletedEvent(5L));

        assertEquals(List.of(), writer.records());
    }

    private void subscribe(String scopePolicyRef) {
        subscribe(scopePolicyRef, event -> {
        });
    }

    private void subscribe(String scopePolicyRef, Consumer<Object> behaviour) {
        PluginRuntimeDescriptor.EventSubscription subscription = new PluginRuntimeDescriptor.EventSubscription(
            "first-owner-invitee-setup", InviteeSetupEventDelivery.EVENT_TYPE, "^1.0", "first-owner-invitee-setup.v1",
            "no-retry", "invitation-reissue", scopePolicyRef);
        EventHandler<FirstOwnerInviteeSetupCompletedEvent> handler = invocation -> {
            handled.add(TenantScopes.requested());
            behaviour.accept(invocation.event().payload());
            return CompletableFuture.completedFuture(null);
        };
        subscribers.add(new BoundEventHandler(OWNER, subscription, handler, FirstOwnerInviteeSetupCompletedEvent.class));
    }

    @SuppressWarnings("unchecked")
    private InviteeSetupEventDelivery delivery() {
        ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(identities);
        return new InviteeSetupEventDelivery(
            eventType -> InviteeSetupEventDelivery.EVENT_TYPE.equals(eventType) ? subscribers : List.of(),
            owner -> OWNER.equals(owner) ? Optional.of(persistence) : Optional.empty(), provider, () -> writer);
    }
}
