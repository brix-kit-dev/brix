/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;

/**
 * Low-cardinality backlog view used by L2B readiness and metrics.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OutboxBacklogSnapshot(long pendingCount, long inFlightCount, long parkedCount, Duration oldestPendingAge) {

    /**
     * Creates a backlog snapshot.
     */
    public OutboxBacklogSnapshot {
        if (pendingCount < 0 || inFlightCount < 0 || parkedCount < 0) {
            throw new IllegalArgumentException("backlog counts must be >= 0");
        }
        oldestPendingAge = oldestPendingAge != null ? oldestPendingAge : Duration.ZERO;
        if (oldestPendingAge.isNegative()) {
            oldestPendingAge = Duration.ZERO;
        }
    }
}
