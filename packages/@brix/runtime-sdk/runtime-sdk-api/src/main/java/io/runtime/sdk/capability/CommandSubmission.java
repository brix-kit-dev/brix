/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * A submitted command (root BRX-RS-MESSAGE-012): its id at once, and a receipt that completes only once the
 * command is durable.
 *
 * @param commandId the command's id, stable across retries
 * @param durableReceipt completes after the submitting unit of work commits, exceptionally when it rolls back
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record CommandSubmission(String commandId, CompletionStage<CommandReceipt> durableReceipt) {

    /**
     * Creates a submission.
     */
    public CommandSubmission {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId must not be blank");
        }
        Objects.requireNonNull(durableReceipt, "durableReceipt must not be null");
    }
}
