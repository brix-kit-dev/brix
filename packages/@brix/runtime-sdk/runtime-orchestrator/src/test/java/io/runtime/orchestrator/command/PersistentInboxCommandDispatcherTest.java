/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.ConsumerUnitOfWork;
import io.runtime.sdk.command.CommandEnvelope;
import io.runtime.sdk.event.EventScope;

class PersistentInboxCommandDispatcherTest {

    @Test
    void duplicateCommandIdDoesNotRunSideEffectTwice() {
        Stores stores = new Stores();
        AtomicInteger sideEffects = new AtomicInteger();
        PersistentInboxCommandDispatcher dispatcher = dispatcher(stores, sideEffects);

        var first = dispatcher.dispatch(command("command-1", "idem-1"), 1);
        var duplicate = dispatcher.dispatch(command("command-1", "idem-1"), 2);

        assertTrue(first.handled());
        assertTrue(duplicate.duplicateCommand());
        assertEquals(1, sideEffects.get());
    }

    @Test
    void duplicateBusinessIdempotencyKeyDoesNotRunSideEffectTwice() {
        Stores stores = new Stores();
        AtomicInteger sideEffects = new AtomicInteger();
        PersistentInboxCommandDispatcher dispatcher = dispatcher(stores, sideEffects);

        var first = dispatcher.dispatch(command("command-1", "idem-1"), 1);
        var duplicateBusiness = dispatcher.dispatch(command("command-2", "idem-1"), 1);

        assertTrue(first.handled());
        assertTrue(duplicateBusiness.duplicateBusinessKey());
        assertEquals(1, sideEffects.get());
    }

    @Test
    void handlerFailureRollsBackInboxAndIdempotencySoRetryCanRun() {
        Stores stores = new Stores();
        AtomicInteger attempts = new AtomicInteger();
        PersistentInboxCommandDispatcher dispatcher = dispatcher(stores, ignored -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt failed");
            }
        });

        assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(command("command-1", "idem-1"), 1));
        var retry = dispatcher.dispatch(command("command-1", "idem-1"), 2);

        assertTrue(retry.handled());
        assertEquals(2, attempts.get());
    }

    @Test
    void unregisteredHandlerIsOfflineNotDuplicate() {
        PersistentInboxCommandDispatcher dispatcher = new PersistentInboxCommandDispatcher(
            new Stores(),
            new Stores(),
            new Stores(),
            Duration.ofSeconds(5));

        assertThrows(CommandHandlerOfflineException.class, () -> dispatcher.dispatch(command("command-1", "idem-1"), 1));
    }

    private static PersistentInboxCommandDispatcher dispatcher(Stores stores, AtomicInteger sideEffects) {
        return dispatcher(stores, ignored -> sideEffects.incrementAndGet());
    }

    private static PersistentInboxCommandDispatcher dispatcher(Stores stores, java.util.function.Consumer<String> sideEffect) {
        PersistentInboxCommandDispatcher dispatcher = new PersistentInboxCommandDispatcher(
            stores,
            stores,
            stores,
            Duration.ofSeconds(5));
        dispatcher.register("TenantMemberCommand", "tenant-member-command.v1", invocation -> {
            sideEffect.accept(invocation.command().payload());
            return CompletableFuture.completedFuture(null);
        });
        return dispatcher;
    }

    private static CommandEnvelope<String> command(String commandId, String idempotencyKey) {
        return new CommandEnvelope<>(
            commandId,
            "TenantMemberCommand",
            "1.0.0",
            Instant.parse("2026-07-28T00:00:00Z"),
            "sender-plugin",
            EventScope.TENANT,
            "100",
            "correlation-1",
            null,
            "00-00000000000000000000000000000001-0000000000000001-01",
            null,
            "tenant-100",
            "tenant-member",
            idempotencyKey,
            "payload");
    }

    private static final class Stores implements ConsumerUnitOfWork, CommandInboxStore, CommandIdempotencyStore {
        private Set<String> inbox = new HashSet<>();
        private Set<String> idempotency = new HashSet<>();

        @Override
        public void execute(Runnable work) {
            Set<String> inboxSnapshot = new HashSet<>(inbox);
            Set<String> idempotencySnapshot = new HashSet<>(idempotency);
            try {
                work.run();
            } catch (RuntimeException ex) {
                inbox = inboxSnapshot;
                idempotency = idempotencySnapshot;
                throw ex;
            }
        }

        @Override
        public boolean insertReceived(String handlerId, CommandEnvelope<?> command) {
            return inbox.add(handlerId + ":" + command.commandId());
        }

        @Override
        public boolean reserve(String handlerId, CommandEnvelope<?> command) {
            assertFalse(command.idempotencyScope().isBlank());
            assertFalse(command.idempotencyKey().isBlank());
            return idempotency.add(handlerId + ":" + command.idempotencyScope() + ":" + command.idempotencyKey());
        }
    }
}
