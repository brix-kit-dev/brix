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
 * Immutable invocation passed to a manifest-declared command handler.
 *
 * @param command command envelope reconstructed by Runtime Shell
 * @param deadline invocation deadline
 * @param deliveryAttempt durable delivery attempt count
 * @param <C> command payload type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record CommandInvocation<C>(
        CommandEnvelope<C> command,
        Instant deadline,
        int deliveryAttempt) {

    /**
     * Creates a validated command invocation.
     */
    public CommandInvocation {
        command = Objects.requireNonNull(command, "command must not be null");
        deadline = Objects.requireNonNull(deadline, "deadline must not be null");
        if (deliveryAttempt <= 0) {
            throw new IllegalArgumentException("deliveryAttempt must be positive");
        }
    }
}
