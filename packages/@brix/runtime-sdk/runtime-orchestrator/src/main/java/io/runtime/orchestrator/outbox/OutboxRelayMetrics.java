/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

/**
 * Low-cardinality metrics sink for the L2B outbox relay.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface OutboxRelayMetrics {

    /**
     * No-op metrics sink for tests and minimal hosts.
     */
    OutboxRelayMetrics NOOP = new OutboxRelayMetrics() {
    };

    default void recordBacklog(OutboxBacklogSnapshot snapshot) {
    }

    default void recordClaimed(int count) {
    }

    default void recordPublished(String messageType) {
    }

    default void recordRetry(String messageType, String errorCode) {
    }

    default void recordParked(String messageType, String errorCode) {
    }
}
