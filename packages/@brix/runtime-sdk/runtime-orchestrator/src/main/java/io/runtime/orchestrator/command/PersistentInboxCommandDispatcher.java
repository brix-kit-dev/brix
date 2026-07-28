/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

import io.runtime.orchestrator.event.ConsumerUnitOfWork;
import io.runtime.sdk.command.CommandEnvelope;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.CommandInvocation;

/**
 * Runtime Command Dispatcher backed by persistent Inbox and idempotency stores.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PersistentInboxCommandDispatcher {

    private final ConsumerUnitOfWork unitOfWork;
    private final CommandInboxStore inboxStore;
    private final CommandIdempotencyStore idempotencyStore;
    private final Duration handlerTimeout;
    private final Map<String, Binding> bindingsByCommandType = new ConcurrentHashMap<>();

    /**
     * Creates a command dispatcher.
     *
     * @param unitOfWork Consumer Owner transaction boundary
     * @param inboxStore persistent command inbox store
     * @param idempotencyStore business idempotency store
     * @param handlerTimeout handler timeout
     */
    public PersistentInboxCommandDispatcher(
            ConsumerUnitOfWork unitOfWork,
            CommandInboxStore inboxStore,
            CommandIdempotencyStore idempotencyStore,
            Duration handlerTimeout) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork must not be null");
        this.inboxStore = Objects.requireNonNull(inboxStore, "inboxStore must not be null");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore must not be null");
        this.handlerTimeout = Objects.requireNonNull(handlerTimeout, "handlerTimeout must not be null");
        if (handlerTimeout.isZero() || handlerTimeout.isNegative()) {
            throw new IllegalArgumentException("handlerTimeout must be positive");
        }
    }

    /**
     * Registers the unique manifest-declared command owner handler.
     *
     * @param commandType command type
     * @param handlerId manifest handler id
     * @param handler handler
     */
    public void register(String commandType, String handlerId, CommandHandler<String> handler) {
        requireText(commandType, "commandType");
        requireText(handlerId, "handlerId");
        Objects.requireNonNull(handler, "handler must not be null");
        Binding previous = bindingsByCommandType.putIfAbsent(commandType, new Binding(handlerId, handler));
        if (previous != null) {
            throw new CommandDispatchException("Duplicate command handler for command type: " + commandType);
        }
    }

    /**
     * Dispatches a command envelope to its unique owner.
     *
     * @param command command envelope
     * @param deliveryAttempt delivery attempt count
     * @return dispatch result
     */
    public DispatchResult dispatch(CommandEnvelope<String> command, int deliveryAttempt) {
        Objects.requireNonNull(command, "command must not be null");
        Binding binding = bindingsByCommandType.get(command.commandType());
        if (binding == null) {
            throw new CommandHandlerOfflineException(command.commandType());
        }
        DispatchResult result = new DispatchResult();
        unitOfWork.execute(() -> {
            if (!inboxStore.insertReceived(binding.handlerId(), command)) {
                result.duplicateCommand = true;
                return;
            }
            if (!idempotencyStore.reserve(binding.handlerId(), command)) {
                result.duplicateBusinessKey = true;
                return;
            }
            CompletionStage<Void> completion = binding.handler().handle(new CommandInvocation<>(
                command,
                Instant.now().plus(handlerTimeout),
                deliveryAttempt));
            completion.toCompletableFuture().join();
            result.handled = true;
        });
        return result.snapshot();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private record Binding(String handlerId, CommandHandler<String> handler) {
    }

    /**
     * Command dispatch counters.
     */
    public static final class DispatchResult {
        private boolean handled;
        private boolean duplicateCommand;
        private boolean duplicateBusinessKey;

        private DispatchResult snapshot() {
            DispatchResult copy = new DispatchResult();
            copy.handled = handled;
            copy.duplicateCommand = duplicateCommand;
            copy.duplicateBusinessKey = duplicateBusinessKey;
            return copy;
        }

        public boolean handled() {
            return handled;
        }

        public boolean duplicateCommand() {
            return duplicateCommand;
        }

        public boolean duplicateBusinessKey() {
            return duplicateBusinessKey;
        }
    }
}
