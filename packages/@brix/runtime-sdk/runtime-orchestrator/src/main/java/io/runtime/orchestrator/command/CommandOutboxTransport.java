/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.Objects;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransport;
import io.runtime.orchestrator.outbox.OutboxTransportException;

/**
 * Outbox transport adapter that dispatches canonical command messages.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class CommandOutboxTransport implements OutboxTransport {

    private static final String MESSAGE_KIND_COMMAND = "COMMAND";

    private final CommandPayloadCodec payloadCodec;
    private final PersistentInboxCommandDispatcher dispatcher;

    /**
     * Creates a command outbox transport.
     *
     * @param payloadCodec command payload codec
     * @param dispatcher command dispatcher
     */
    public CommandOutboxTransport(
            CommandPayloadCodec payloadCodec,
            PersistentInboxCommandDispatcher dispatcher) {
        this.payloadCodec = Objects.requireNonNull(payloadCodec, "payloadCodec must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    }

    @Override
    public void publish(CanonicalOutboxMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        if (!MESSAGE_KIND_COMMAND.equals(message.messageKind())) {
            return;
        }
        try {
            dispatcher.dispatch(payloadCodec.decode(message), Math.max(1, message.attemptCount()));
        } catch (CommandHandlerOfflineException ex) {
            throw new OutboxTransportException("COMMAND_HANDLER_OFFLINE", ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new OutboxTransportException("COMMAND_DISPATCH_FAILED", "command dispatch failed", ex);
        }
    }
}
