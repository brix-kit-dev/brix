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
 * Durable acceptance of a command (root BRX-RS-MESSAGE-013): it is persisted and queued for delivery. It does not
 * say that the provider accepted or completed it.
 *
 * @param commandId the command's id
 * @param commandType the command type
 * @param persistedAt when the command was written
 * @param status always {@link Status#QUEUED}
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record CommandReceipt(String commandId, String commandType, Instant persistedAt, Status status) {

    /** What a receipt can say. */
    public enum Status {
        /** The command is durable and waits for delivery. */
        QUEUED
    }

    /**
     * Creates a receipt.
     */
    public CommandReceipt {
        commandId = requireText(commandId, "commandId");
        commandType = requireText(commandType, "commandType");
        persistedAt = Objects.requireNonNull(persistedAt, "persistedAt must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
