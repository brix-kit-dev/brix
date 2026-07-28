/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import io.runtime.sdk.command.CommandEnvelope;

/**
 * L2B boundary for business command idempotency results.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface CommandIdempotencyStore {

    /**
     * Reserves a business idempotency key.
     *
     * @param handlerId manifest handler id
     * @param command command envelope carrying idempotency metadata
     * @return true when side effects should run
     */
    boolean reserve(String handlerId, CommandEnvelope<?> command);
}
