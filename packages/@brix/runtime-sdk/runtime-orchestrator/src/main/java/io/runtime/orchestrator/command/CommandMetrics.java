/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

/**
 * Meters of command delivery (standard-link plan point 8).
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface CommandMetrics {

    /** Records nothing. */
    CommandMetrics NOOP = (ownerId, handlerId) -> {
    };

    /**
     * Records a command that reused an idempotency key with other content: a mistake of its sender, which the
     * sender reads as a conflict.
     *
     * @param ownerId the providing plugin
     * @param handlerId the provider's handler
     */
    void conflict(String ownerId, String handlerId);
}
