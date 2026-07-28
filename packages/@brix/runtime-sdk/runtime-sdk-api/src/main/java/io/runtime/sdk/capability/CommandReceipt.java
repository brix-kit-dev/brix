/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable sender receipt for a typed command.
 *
 * <p>The receipt only proves that the sender's canonical outbox record has
 * committed. It does not claim that the target handler has run or that business
 * side effects have completed.</p>
 *
 * @param commandId stable command identity
 * @param commandType versioned command contract id
 * @param acceptedAt time the durable receipt was completed after commit
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record CommandReceipt(String commandId, String commandType, Instant acceptedAt) {

    /**
     * Creates a validated receipt.
     */
    public CommandReceipt {
        commandId = requireText(commandId, "commandId");
        commandType = requireText(commandType, "commandType");
        acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
