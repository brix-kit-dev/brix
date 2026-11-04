/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.Optional;

/**
 * The commands a plugin sent, as its own outbox still holds them (standard-link plan point 7). A command's status
 * comes from here until its owner has a record of it.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface SentCommands {

    /** A sender whose outbox tells nothing. */
    SentCommands NONE = commandId -> Optional.empty();

    /**
     * Finds a command in the sender's outbox.
     *
     * @param commandId the command id
     * @return its row, or empty when the outbox does not hold it, or no longer does
     */
    Optional<SentCommand> sent(String commandId);
}
