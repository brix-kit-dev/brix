/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

/**
 * Raised when no command handler is currently registered for a command type.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class CommandHandlerOfflineException extends CommandDispatchException {

    /**
     * Creates an offline-handler exception.
     *
     * @param commandType command type
     */
    public CommandHandlerOfflineException(String commandType) {
        super("Command handler offline for command type: " + commandType);
    }
}
