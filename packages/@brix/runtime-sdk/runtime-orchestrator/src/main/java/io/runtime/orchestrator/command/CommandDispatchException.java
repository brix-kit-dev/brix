/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

/**
 * Runtime command dispatch failure.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class CommandDispatchException extends RuntimeException {

    /**
     * Creates a dispatch exception.
     *
     * @param message safe diagnostic message
     */
    public CommandDispatchException(String message) {
        super(message);
    }
}
