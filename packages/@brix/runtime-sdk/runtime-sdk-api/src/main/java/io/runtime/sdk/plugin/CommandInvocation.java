/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.time.Instant;
import java.util.Objects;

import io.runtime.sdk.command.CommandEnvelope;

/**
 * Immutable invocation passed to a manifest-declared command handler (root BRX-RS-MANIFEST-027, MANIFEST-028).
 *
 * @param command command envelope reconstructed by Runtime Shell
 * @param access what the owner's access policy admitted: for an {@code on-behalf-of:<permission>} policy, the
 *     permission the sender's user was verified to hold when the command was submitted; empty otherwise
 * @param trace the trace context the handler runs in
 * @param deadline invocation deadline
 * @param cancellation tells the handler when the runtime no longer waits for it
 * @param idempotency the business key the command is applied at most once for
 * @param deliveryAttempt durable delivery attempt count
 * @param <C> command payload type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record CommandInvocation<C>(
        CommandEnvelope<C> command,
        AccessDecision access,
        TraceContext trace,
        Instant deadline,
        CancellationSignal cancellation,
        IdempotencyContext idempotency,
        int deliveryAttempt) {

    /**
     * Creates an invocation; the runtime builds it (root BRX-RS-MANIFEST-028).
     */
    public CommandInvocation {
        command = Objects.requireNonNull(command, "command must not be null");
        access = access != null ? access : AccessDecision.none();
        trace = Objects.requireNonNull(trace, "trace must not be null");
        deadline = Objects.requireNonNull(deadline, "deadline must not be null");
        cancellation = Objects.requireNonNull(cancellation, "cancellation must not be null");
        idempotency = idempotency != null
            ? idempotency
            : new IdempotencyContext(command.idempotencyScope(), command.idempotencyKey());
        if (deliveryAttempt <= 0) {
            throw new IllegalArgumentException("deliveryAttempt must be positive");
        }
    }

    /**
     * Creates an invocation with no access decision and the envelope's idempotency key, as runtimes before 3.3.0
     * did.
     *
     * @param command command envelope reconstructed by Runtime Shell
     * @param trace the trace context the handler runs in
     * @param deadline invocation deadline
     * @param cancellation tells the handler when the runtime no longer waits for it
     * @param deliveryAttempt durable delivery attempt count
     */
    public CommandInvocation(CommandEnvelope<C> command, TraceContext trace, Instant deadline,
            CancellationSignal cancellation, int deliveryAttempt) {
        this(command, null, trace, deadline, cancellation, null, deliveryAttempt);
    }
}
