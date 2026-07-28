/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import io.runtime.sdk.command.CommandEnvelope;

/**
 * L2B boundary for persistent Command Inbox receipts.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface CommandInboxStore {

    /**
     * Inserts a command receipt keyed by {@code (handlerId, commandId)}.
     *
     * @param handlerId manifest handler id
     * @param command command envelope
     * @return true when this delivery should invoke the handler
     */
    boolean insertReceived(String handlerId, CommandEnvelope<?> command);
}
