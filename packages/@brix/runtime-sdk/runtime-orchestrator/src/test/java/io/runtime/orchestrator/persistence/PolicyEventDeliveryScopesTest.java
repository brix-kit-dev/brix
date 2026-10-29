/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.EventSubscribers;
import io.runtime.orchestrator.event.InProcessEventTransport;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.error.PolicyAuditException;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.EventHandler;

/**
 * The post-setup resend through the outbox relay's in-process delivery (P4 design §4.3, D16): the runtime
 * binds the {@code first-owner-invitee-setup-fanout} scope and records each use in the platform audit.
 */
class PolicyEventDeliveryScopesTest {

    private static final String OWNER = "platform-tenant";
    private static final String EVENT_TYPE = "FirstOwnerInviteeSetupCompleted";
    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final List<TenantScope> handled = new ArrayList<>();
    private final RecordingPersistence persistence = new RecordingPersistence();
    private final FirstOwnerInviteeIdentitySetupCapability identities = mock(FirstOwnerInviteeIdentitySetupCapability.class);
    private final RecordingPolicyAuditWriter writer = new RecordingPolicyAuditWriter().watching(persistence::active);
    private final Set<String> receipts = new HashSet<>();
    private BoundEventHandler bound;

    @Test
    void theResendIsKeyedByTheEmailOfTheIdentityThatCompletedSetup() {
        subscribe("first-owner-invitee-setup-fanout");
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");

        transport().publish(setupCompleted("event-1"));

        TenantScope fanout = TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, "owner@example.com");
        assertEquals(List.of(fanout), handled);
        assertEquals(List.of("required"), persistence.modes());
        assertEquals(List.of(fanout), persistence.scopes());
        assertEquals(TenantScope.none(), TenantScopes.requested(), "the scope must not outlive the delivery");
    }

    @Test
    void theResendIsAuditedInsideTheOwnersUnitOfWorkWithTheTenantsItReached() {
        subscribe("first-owner-invitee-setup-fanout", event -> {
            TenantScopes.reportBound(910001L);
            TenantScopes.reportBound(910002L);
        });
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");

        transport().publish(setupCompleted("event-1"));

        PolicyUseRecord record = writer.records().get(0);
        assertEquals(List.of(true), writer.insideUnitOfWork(), "the record must be written before the owner commits");
        assertEquals("FIRST_OWNER_INVITEE_SETUP_FANOUT", record.action());
        assertEquals(5L, record.actorIdentityId());
        assertEquals(List.of(910001L, 910002L), record.affectedTenants());
        assertEquals(Map.of("subscription", "first-owner-invitee-setup"), record.context());
        assertEquals(true, record.success());
        assertEquals(TRACE, record.traceId(), "the record shares the delivered fact's trace");
    }

    @Test
    void aRedeliveredFactThatTheInboxSkipsIsNotAnotherUse() {
        subscribe("first-owner-invitee-setup-fanout");
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");
        InProcessEventTransport transport = transport();

        transport.publish(setupCompleted("event-1"));
        transport.publish(setupCompleted("event-1"));

        assertEquals(1, handled.size(), "the inbox kept the handler from resending twice");
        assertEquals(1, writer.records().size());
    }

    @Test
    void whenTheRecordCannotBeWrittenTheResendRollsBackWithoutAFailureRecord() {
        subscribe("first-owner-invitee-setup-fanout");
        when(identities.requireActiveIdentityEmail(5L)).thenReturn("owner@example.com");
        PolicyAuditException refused = new PolicyAuditException(Map.of("policy", "first-owner-invitee-setup-fanout"));
        writer.failingWith(refused);

        OutboxTransportException retry = assertThrows(OutboxTransportException.class,
            () -> transport().publish(setupCompleted("event-1")));

        assertFalse(retry.permanent(), "the relay retries the event");
        assertSame(refused, retry.getCause());
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

        OutboxTransportException retry = assertThrows(OutboxTransportException.class,
            () -> transport().publish(setupCompleted("event-1")));

        assertSame(broken, retry.getCause());
        PolicyUseRecord record = writer.records().get(0);
        assertEquals(List.of(false), writer.insideUnitOfWork(), "the failure record is written after the rollback");
        assertEquals(false, record.success());
        assertEquals("policy.handler_failed", record.errorCode());
        assertEquals(List.of(910001L), record.affectedTenants());
    }

    @Test
    void subscriptionsWithoutARegisteredPolicyKeepTheEnvelopeScopeAndWriteNoRecord() {
        subscribe("tenant-first-owner-projection-scope");

        transport().publish(setupCompleted("event-1"));

        assertEquals(List.of(TenantScope.none()), handled);
        assertEquals(List.of(), writer.records());
    }

    @Test
    void subscriptionsCannotChooseAKeyTheRuntimeMustBind() {
        subscribe("login-tenant-discovery");

        transport().publish(setupCompleted("event-1"));

        assertEquals(List.of(TenantScope.none()), handled);
        assertEquals(List.of(), writer.records());
    }

    @Test
    void withoutTheIdentityOwnerTheResendWaitsForARetry() {
        subscribe("first-owner-invitee-setup-fanout");

        OutboxTransportException retry = assertThrows(OutboxTransportException.class,
            () -> new InProcessEventTransport(subscribers(), owner -> Optional.of(persistence), this::receipt,
                new ObjectMapper(), Clock.systemUTC(), Duration.ofSeconds(30),
                new PolicyEventDeliveryScopes(() -> null, () -> writer)).publish(setupCompleted("event-1")));

        assertFalse(retry.permanent());
        assertEquals(List.of(), handled, "no tenant data is reached without the access key");
    }

    private void subscribe(String scopePolicyRef) {
        subscribe(scopePolicyRef, event -> {
        });
    }

    private void subscribe(String scopePolicyRef, Consumer<Object> behaviour) {
        PluginRuntimeDescriptor.EventSubscription subscription = new PluginRuntimeDescriptor.EventSubscription(
            "first-owner-invitee-setup", EVENT_TYPE, ">=1.0.0 <2.0.0", "first-owner-invitee-setup.v1",
            "event-standard", "persistent-inbox", scopePolicyRef);
        EventHandler<FirstOwnerInviteeSetupCompletedEvent> handler = invocation -> {
            handled.add(TenantScopes.requested());
            behaviour.accept(invocation.event().payload());
            return CompletableFuture.completedFuture(null);
        };
        bound = new BoundEventHandler(OWNER, subscription, handler, FirstOwnerInviteeSetupCompletedEvent.class);
    }

    private InProcessEventTransport transport() {
        return new InProcessEventTransport(subscribers(), owner -> OWNER.equals(owner) ? Optional.of(persistence) : Optional.empty(),
            this::receipt, new ObjectMapper(), Clock.systemUTC(), Duration.ofSeconds(30),
            new PolicyEventDeliveryScopes(() -> identities, () -> writer));
    }

    /** The persistent inbox: one receipt per handler and event. */
    private boolean receipt(String owner, String handlerId, CanonicalOutboxMessage message) {
        return receipts.add(handlerId + "/" + message.eventId());
    }

    private EventSubscribers subscribers() {
        return new EventSubscribers() {
            @Override
            public boolean ready() {
                return true;
            }

            @Override
            public Optional<String> publisherOf(String eventType) {
                return EVENT_TYPE.equals(eventType) ? Optional.of("platform-identity") : Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return EVENT_TYPE.equals(eventType) ? List.of(new DeclaredSubscriber(OWNER, bound.subscription(), bound)) : List.of();
            }
        };
    }

    /** The fact as the identity owner's outbox records it. */
    private static CanonicalOutboxMessage setupCompleted(String eventId) {
        return new CanonicalOutboxMessage(eventId, eventId, "EVENT", EVENT_TYPE, "1.0.0", "CRITICAL", "platform-identity",
            "PLATFORM", null, "IDENTITY:5", OffsetDateTime.now(), eventId, null, "00-" + TRACE + "-00f067aa0ba902b7-01",
            null, "{\"identityId\":5}", 1);
    }
}
