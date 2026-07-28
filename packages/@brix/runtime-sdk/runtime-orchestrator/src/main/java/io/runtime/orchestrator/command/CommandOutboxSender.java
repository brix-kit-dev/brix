/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.CommandReceipt;
import io.runtime.sdk.capability.CommandSubmitException;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.command.CommandEnvelope;

/**
 * Runtime-native command capability backed by the canonical sender outbox.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@Capability(
    type = CommandCapability.class,
    name = "canonical-outbox-command",
    description = "Runtime command capability backed by the canonical sender outbox",
    level = CapabilityLevel.CORE,
    priority = 100)
public final class CommandOutboxSender implements CommandCapability {

    private static final String MESSAGE_KIND_COMMAND = "COMMAND";
    private static final String RELIABILITY_STANDARD = "STANDARD";

    private final OutboxMessageStore outboxStore;
    private final CommandTransactionBoundary transactionBoundary;
    private final CommandPayloadCodec payloadCodec;
    private final Clock clock;

    /**
     * Creates a command sender.
     *
     * @param outboxStore canonical outbox store
     * @param transactionBoundary transaction callback boundary
     * @param payloadCodec command payload codec
     * @param clock clock for receipts
     */
    public CommandOutboxSender(
            OutboxMessageStore outboxStore,
            CommandTransactionBoundary transactionBoundary,
            CommandPayloadCodec payloadCodec,
            Clock clock) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore must not be null");
        this.transactionBoundary = Objects.requireNonNull(transactionBoundary, "transactionBoundary must not be null");
        this.payloadCodec = Objects.requireNonNull(payloadCodec, "payloadCodec must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public <C> CompletionStage<CommandReceipt> submit(CommandEnvelope<C> command) {
        Objects.requireNonNull(command, "command must not be null");
        CompletableFuture<CommandReceipt> receipt = new CompletableFuture<>();
        CanonicalOutboxMessage message = toOutbox(command);
        try {
            transactionBoundary.register(
                () -> receipt.complete(new CommandReceipt(
                    command.commandId(),
                    command.commandType(),
                    clock.instant())),
                () -> receipt.completeExceptionally(new CommandSubmitException(
                    CommandSubmitException.Code.TRANSACTION_ROLLED_BACK,
                    Map.of("commandId", command.commandId()),
                    null)));
            outboxStore.append(message);
        } catch (CommandSubmitException ex) {
            receipt.completeExceptionally(ex);
        } catch (RuntimeException ex) {
            receipt.completeExceptionally(new CommandSubmitException(
                CommandSubmitException.Code.OUTBOX_APPEND_FAILED,
                Map.of("commandId", command.commandId()),
                ex));
        }
        return receipt;
    }

    private CanonicalOutboxMessage toOutbox(CommandEnvelope<?> command) {
        Long tenantId = command.tenantId() == null || command.tenantId().isBlank()
            ? null
            : Long.valueOf(command.tenantId());
        return new CanonicalOutboxMessage(
            command.commandId(),
            command.commandId(),
            MESSAGE_KIND_COMMAND,
            command.commandType(),
            command.schemaVersion(),
            RELIABILITY_STANDARD,
            command.producerPluginId(),
            command.scope().name(),
            tenantId,
            command.partitionKey(),
            OffsetDateTime.ofInstant(command.submittedAt(), ZoneOffset.UTC),
            command.correlationId(),
            command.causationId(),
            command.traceparent(),
            command.tracestate(),
            payloadCodec.encode(command),
            0);
    }
}
