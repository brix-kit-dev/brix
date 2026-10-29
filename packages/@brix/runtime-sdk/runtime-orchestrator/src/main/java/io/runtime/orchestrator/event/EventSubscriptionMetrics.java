/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Duration;

/**
 * Low-cardinality metrics of one subscription a broker consumes for (Kafka design §4.5).
 */
public interface EventSubscriptionMetrics {

    /** Records nothing, for tests and minimal hosts. */
    EventSubscriptionMetrics NOOP = new EventSubscriptionMetrics() {
    };

    default void recordDelivered(String eventType) {
    }

    default void recordRetry(String eventType, String errorCode) {
    }

    default void recordParked(String eventType, String errorCode) {
    }

    /** How many events the subscription holds parked now. */
    default void recordParkedNow(int count) {
    }

    /** How many records wait behind the consumer's position. */
    default void recordLag(long records) {
    }

    /** How long the record at the head of a partition has been failing; zero when none is. */
    default void recordStalled(Duration age) {
    }
}
