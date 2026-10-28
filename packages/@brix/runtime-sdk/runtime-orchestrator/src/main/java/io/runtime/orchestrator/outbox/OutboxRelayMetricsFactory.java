/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

/**
 * Creates the metrics of the relay that serves one owner outbox.
 */
@FunctionalInterface
public interface OutboxRelayMetricsFactory {

    /** Records nothing. */
    OutboxRelayMetricsFactory NOOP = outbox -> OutboxRelayMetrics.NOOP;

    /**
     * Returns the metrics of one outbox.
     *
     * @param outbox the owner's outbox table
     * @return its relay metrics
     */
    OutboxRelayMetrics forOutbox(String outbox);
}
