/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.ConsumerUnitOfWork;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;

class CommandOutboxTransportTest {

    @Test
    void offlineHandlerFailsTemporarilyAndRecoveryUsesSameCommandIdentity() {
        CommandPayloadCodec codec = new JsonCommandPayloadCodec(new ObjectMapper());
        Stores stores = new Stores();
        AtomicInteger handled = new AtomicInteger();
        PersistentInboxCommandDispatcher dispatcher = new PersistentInboxCommandDispatcher(
            stores,
            stores,
            stores,
            Duration.ofSeconds(5));
        CommandOutboxTransport transport = new CommandOutboxTransport(codec, dispatcher);
        CanonicalOutboxMessage message = message(codec);

        OutboxTransportException offline = assertThrows(OutboxTransportException.class, () -> transport.publish(message));
        assertEquals("COMMAND_HANDLER_OFFLINE", offline.errorCode());

        dispatcher.register("TenantMemberCommand", "tenant-member-command.v1", invocation -> {
            handled.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        transport.publish(message);
        transport.publish(message);

        assertEquals(1, handled.get());
        assertEquals("command-1", codec.decode(message).commandId());
        assertEquals("idem-1", codec.decode(message).idempotencyKey());
    }

    private static CanonicalOutboxMessage message(CommandPayloadCodec codec) {
        String payload = """
            {"idempotencyScope":"tenant-member","idempotencyKey":"idem-1","payload":"body"}
            """;
        return new CanonicalOutboxMessage(
            "command-1",
            "command-1",
            "COMMAND",
            "TenantMemberCommand",
            "1.0.0",
            "STANDARD",
            "sender-plugin",
            "TENANT",
            100L,
            "tenant-100",
            OffsetDateTime.parse("2026-07-28T00:00:00Z"),
            "correlation-1",
            null,
            "00-00000000000000000000000000000001-0000000000000001-01",
            null,
            payload,
            1);
    }

    private static final class Stores implements ConsumerUnitOfWork, CommandInboxStore, CommandIdempotencyStore {
        private final java.util.Set<String> inbox = new java.util.HashSet<>();
        private final java.util.Set<String> idempotency = new java.util.HashSet<>();

        @Override
        public void execute(Runnable work) {
            work.run();
        }

        @Override
        public boolean insertReceived(String handlerId, io.runtime.sdk.command.CommandEnvelope<?> command) {
            return inbox.add(handlerId + ":" + command.commandId());
        }

        @Override
        public boolean reserve(String handlerId, io.runtime.sdk.command.CommandEnvelope<?> command) {
            return idempotency.add(handlerId + ":" + command.idempotencyScope() + ":" + command.idempotencyKey());
        }
    }
}
