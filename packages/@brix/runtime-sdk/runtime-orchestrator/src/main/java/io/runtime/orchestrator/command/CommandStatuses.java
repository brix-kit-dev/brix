/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import io.runtime.sdk.command.CommandStatus;

/**
 * Reads where a command stands for its sender (root BRX-RS-MESSAGE-013; standard-link plan point 7).
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface CommandStatuses {

    /** A host that cannot tell: every command reads as unknown. */
    CommandStatuses NONE = (senderId, commandType, tenantId, commandId, sent) -> CommandStatus.unknown(commandId);

    /**
     * Reads a command's status for the plugin that asks.
     *
     * @param senderId the plugin that asks, which must be the one that submitted the command
     * @param commandType the command type of the client that asks
     * @param tenantId the tenant of the call that asks, or {@code null} outside a tenant
     * @param commandId the command id
     * @param sent the asking plugin's outbox
     * @return the status; unknown for a command of another sender or another tenant
     */
    CommandStatus status(String senderId, String commandType, Long tenantId, String commandId, SentCommands sent);
}
