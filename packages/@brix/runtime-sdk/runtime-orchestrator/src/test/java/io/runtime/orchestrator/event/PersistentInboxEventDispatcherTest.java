/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PersistentInboxEventDispatcherTest {

    @Test
    void duplicateDeliverySkipsHandlerAfterCommittedInboxReceipt() {
        TxState state = new TxState();
        PersistentInboxEventDispatcher dispatcher = new PersistentInboxEventDispatcher(
            callback -> state.inTransaction(callback),
            state::insertProcessed);
        AtomicInteger sideEffects = new AtomicInteger();
        dispatcher.register("TenantFirstOwnerAccepted", "tenant-first-owner-projection.v1",
            ignored -> sideEffects.incrementAndGet());

        PersistentInboxEventDispatcher.DispatchResult first = dispatcher.dispatch(message("message-1"));
        PersistentInboxEventDispatcher.DispatchResult duplicate = dispatcher.dispatch(message("message-1"));

        assertEquals(1, first.handledHandlers());
        assertEquals(0, first.duplicateHandlers());
        assertEquals(0, duplicate.handledHandlers());
        assertEquals(1, duplicate.duplicateHandlers());
        assertEquals(1, sideEffects.get());
    }

    @Test
    void handlerFailureRollsBackInboxSoRetryCanRunAgain() {
        TxState state = new TxState();
        PersistentInboxEventDispatcher dispatcher = new PersistentInboxEventDispatcher(
            callback -> state.inTransaction(callback),
            state::insertProcessed);
        AtomicInteger attempts = new AtomicInteger();
        dispatcher.register("TenantFirstOwnerAccepted", "tenant-first-owner-projection.v1", ignored -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("projection failed");
            }
        });

        assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(message("message-2")));
        PersistentInboxEventDispatcher.DispatchResult retry = dispatcher.dispatch(message("message-2"));

        assertEquals(1, retry.handledHandlers());
        assertEquals(2, attempts.get());
    }

    @Test
    void duplicateHandlerBindingFailsFast() {
        PersistentInboxEventDispatcher dispatcher = new PersistentInboxEventDispatcher(Runnable::run,
            (handlerId, message) -> true);
        dispatcher.register("TenantFirstOwnerAccepted", "tenant-first-owner-projection.v1", ignored -> {
        });

        assertThrows(EventBindingException.class, () -> dispatcher.register(
            "TenantFirstOwnerAccepted",
            "tenant-first-owner-projection.v1",
            ignored -> {
            }));
    }

    private static CanonicalEventMessage message(String messageId) {
        return new CanonicalEventMessage(
            messageId,
            "EVENT",
            "TenantFirstOwnerAccepted",
            "1.0.0",
            "platform-tenant",
            "TENANT",
            100L,
            "100",
            OffsetDateTime.now(ZoneOffset.UTC),
            messageId,
            null,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            null,
            "{\"tenantId\":100}");
    }

    private static final class TxState {
        private Set<String> inbox = new HashSet<>();

        void inTransaction(Runnable callback) {
            Set<String> snapshot = new HashSet<>(inbox);
            try {
                callback.run();
            } catch (RuntimeException ex) {
                inbox = snapshot;
                throw ex;
            }
        }

        boolean insertProcessed(String handlerId, CanonicalEventMessage message) {
            return inbox.add(handlerId + ":" + message.messageId());
        }
    }
}
