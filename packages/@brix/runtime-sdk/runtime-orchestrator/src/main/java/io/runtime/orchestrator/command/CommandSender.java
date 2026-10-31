/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.command.CommandRequest;

/**
 * The owner outbox bus a typed command client writes through (standard-link plan L4.2): unlike
 * {@code CommandCapability.submit}, it records the contract version of the sender's generated type and the user the
 * sender acted for.
 */
public interface CommandSender {

    /**
     * Writes a command to the sender's outbox.
     *
     * @param request the command, its business key and its tenant
     * @param schemaVersion the contract version of the sender's generated type
     * @param actorId the user the sender acted for, or {@code null}
     * @return the command id and its durable receipt
     */
    CommandSubmission send(CommandRequest<?> request, String schemaVersion, String actorId);
}
