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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxBacklogSnapshot;
import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.sdk.capability.CommandReceipt;
import io.runtime.sdk.capability.CommandSubmitException;
import io.runtime.sdk.command.CommandEnvelope;
import io.runtime.sdk.event.EventScope;

class CommandOutboxSenderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T00:00:10Z"), ZoneOffset.UTC);

    @Test
    void receiptCompletesOnlyAfterSenderCommit() {
        RecordingOutboxStore store = new RecordingOutboxStore();
        ManualTransactionBoundary transaction = new ManualTransactionBoundary();
        CommandOutboxSender sender = sender(store, transaction);

        var receipt = sender.submit(command("command-1"));

        assertEquals(1, store.messages.size());
        assertEquals("COMMAND", store.messages.get(0).messageKind());
        assertEquals("TenantMemberCommand", store.messages.get(0).messageType());
        assertFalse(receipt.toCompletableFuture().isDone());

        transaction.commit();

        CommandReceipt durable = receipt.toCompletableFuture().join();
        assertEquals("command-1", durable.commandId());
        assertEquals(CLOCK.instant(), durable.acceptedAt());
    }

    @Test
    void receiptCompletesExceptionallyWhenSenderRollsBack() {
        ManualTransactionBoundary transaction = new ManualTransactionBoundary();
        var receipt = sender(new RecordingOutboxStore(), transaction).submit(command("command-2"));

        transaction.rollback();

        CompletionException error = assertThrows(CompletionException.class, () -> receipt.toCompletableFuture().join());
        assertTrue(error.getCause() instanceof CommandSubmitException);
        CommandSubmitException commandError = (CommandSubmitException) error.getCause();
        assertEquals("command.transaction_rolled_back", commandError.errorCode().wireCode());
    }

    @Test
    void commandIdAndIdempotencyKeyArePreservedInCanonicalPayload() {
        RecordingOutboxStore store = new RecordingOutboxStore();
        ManualTransactionBoundary transaction = new ManualTransactionBoundary();
        CommandPayloadCodec codec = new JsonCommandPayloadCodec(new ObjectMapper());

        sender(store, transaction, codec).submit(command("command-3"));

        CommandEnvelope<String> decoded = codec.decode(store.messages.get(0));
        assertEquals("command-3", decoded.commandId());
        assertEquals("tenant-member", decoded.idempotencyScope());
        assertEquals("idem-command-3", decoded.idempotencyKey());
    }

    @Test
    void noActiveTransactionFailsBeforeAppendingOutboxRecord() {
        RecordingOutboxStore store = new RecordingOutboxStore();
        CommandTransactionBoundary transaction = (afterCommit, afterRollback) -> {
            throw new CommandSubmitException(CommandSubmitException.Code.NO_ACTIVE_TRANSACTION, Map.of(), null);
        };

        var receipt = sender(store, transaction).submit(command("command-4"));

        CompletionException error = assertThrows(CompletionException.class, () -> receipt.toCompletableFuture().join());
        assertTrue(error.getCause() instanceof CommandSubmitException);
        CommandSubmitException commandError = (CommandSubmitException) error.getCause();
        assertEquals("command.no_active_transaction", commandError.errorCode().wireCode());
        assertTrue(store.messages.isEmpty());
    }

    private static CommandOutboxSender sender(RecordingOutboxStore store, CommandTransactionBoundary transaction) {
        return sender(store, transaction, new JsonCommandPayloadCodec(new ObjectMapper()));
    }

    private static CommandOutboxSender sender(
            RecordingOutboxStore store,
            CommandTransactionBoundary transaction,
            CommandPayloadCodec codec) {
        return new CommandOutboxSender(store, transaction, codec, CLOCK);
    }

    private static CommandEnvelope<String> command(String commandId) {
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
            "idem-" + commandId,
            "payload");
    }

    private static final class ManualTransactionBoundary implements CommandTransactionBoundary {
        private Runnable afterCommit;
        private Runnable afterRollback;

        @Override
        public void register(Runnable afterCommit, Runnable afterRollback) {
            this.afterCommit = afterCommit;
            this.afterRollback = afterRollback;
        }

        void commit() {
            afterCommit.run();
        }

        void rollback() {
            afterRollback.run();
        }
    }

    private static final class RecordingOutboxStore implements OutboxMessageStore {
        private final List<CanonicalOutboxMessage> messages = new ArrayList<>();

        @Override
        public void append(CanonicalOutboxMessage message) {
            messages.add(message);
        }

        @Override
        public List<CanonicalOutboxMessage> claimDue(
                String relayOwner,
                OffsetDateTime now,
                Duration leaseDuration,
                int batchSize) {
            return List.of();
        }

        @Override
        public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
        }

        @Override
        public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
        }

        @Override
        public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
        }

        @Override
        public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
            return new OutboxBacklogSnapshot(0, 0, 0, Duration.ZERO);
        }
    }
}
