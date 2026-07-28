/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

/**
 * Result of one relay polling pass.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OutboxRelayRunResult(int claimed, int published, int retried, int parked) {

    /**
     * Creates a relay run result.
     */
    public OutboxRelayRunResult {
        if (claimed < 0 || published < 0 || retried < 0 || parked < 0) {
            throw new IllegalArgumentException("relay counters must be >= 0");
        }
    }
}
