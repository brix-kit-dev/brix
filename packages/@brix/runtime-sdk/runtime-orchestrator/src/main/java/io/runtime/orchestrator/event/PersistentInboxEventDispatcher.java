/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.runtime.sdk.plugin.EventHandler;

/**
 * Runtime Event Dispatcher backed by Consumer persistent Inbox receipts.
 *
 * <p>For each matching subscription the dispatcher opens the Consumer Owner
 * unit of work, inserts the canonical Inbox receipt, then invokes the handler.
 * Handler failure propagates to the caller so the Owner transaction can roll
 * back the Inbox receipt and side effects together. Duplicate receipts skip the
 * handler and allow the broker ack to happen after the already-committed work.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PersistentInboxEventDispatcher {

    private final ConsumerUnitOfWork unitOfWork;
    private final ConsumerInboxStore inboxStore;
    private final Map<String, List<Binding>> bindingsByEventType = new ConcurrentHashMap<>();

    /**
     * Creates a dispatcher.
     *
     * @param unitOfWork Consumer Owner transaction boundary
     * @param inboxStore persistent Inbox store
     */
    public PersistentInboxEventDispatcher(
            ConsumerUnitOfWork unitOfWork,
            ConsumerInboxStore inboxStore) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork must not be null");
        this.inboxStore = Objects.requireNonNull(inboxStore, "inboxStore must not be null");
    }

    /**
     * Registers a manifest-declared event handler.
     *
     * @param eventType stable manifest event type
     * @param handlerId stable manifest handler id
     * @param handler handler implementation
     */
    public void register(String eventType, String handlerId, EventHandler<CanonicalEventMessage> handler) {
        requireText(eventType, "eventType");
        requireText(handlerId, "handlerId");
        Objects.requireNonNull(handler, "handler must not be null");
        Binding binding = new Binding(handlerId, handler);
        bindingsByEventType.compute(eventType, (ignored, existing) -> {
            List<Binding> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            if (next.stream().anyMatch(candidate -> candidate.handlerId().equals(handlerId))) {
                throw new EventBindingException("Duplicate event handler binding: " + handlerId);
            }
            next.add(binding);
            return List.copyOf(next);
        });
    }

    /**
     * Dispatches one broker-delivered event to all registered subscribers.
     *
     * @param message canonical event message
     * @return dispatch result
     */
    public DispatchResult dispatch(CanonicalEventMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        List<Binding> bindings = bindingsByEventType.getOrDefault(message.messageType(), List.of());
        DispatchResult result = new DispatchResult(bindings.size());
        for (Binding binding : bindings) {
            unitOfWork.execute(() -> {
                if (!inboxStore.insertProcessed(binding.handlerId(), message)) {
                    result.duplicate();
                    return;
                }
                binding.handler().handle(message);
                result.handled();
            });
        }
        return result.snapshot();
    }

    /**
     * Returns registered handler count for an event type.
     *
     * @param eventType manifest event type
     * @return handler count
     */
    public int handlerCount(String eventType) {
        return bindingsByEventType.getOrDefault(eventType, List.of()).size();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private record Binding(String handlerId, EventHandler<CanonicalEventMessage> handler) {
    }

    /**
     * Dispatch counters for one consumed broker message.
     */
    public static final class DispatchResult {
        private final int matchedHandlers;
        private int handledHandlers;
        private int duplicateHandlers;

        private DispatchResult(int matchedHandlers) {
            this.matchedHandlers = matchedHandlers;
        }

        private void handled() {
            handledHandlers++;
        }

        private void duplicate() {
            duplicateHandlers++;
        }

        private DispatchResult snapshot() {
            DispatchResult copy = new DispatchResult(matchedHandlers);
            copy.handledHandlers = handledHandlers;
            copy.duplicateHandlers = duplicateHandlers;
            return copy;
        }

        public int matchedHandlers() {
            return matchedHandlers;
        }

        public int handledHandlers() {
            return handledHandlers;
        }

        public int duplicateHandlers() {
            return duplicateHandlers;
        }
    }
}
