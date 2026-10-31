/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;

/**
 * The metrics of typed queries (root BRX-RS-OTEL-006; query design §5.7). Labels are bounded: the calling plugin,
 * the query type and the outcome, never a tenant, a user or a request value.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface QueryMetrics {

    /** Records nothing. */
    QueryMetrics NOOP = new QueryMetrics() {
    };

    /** Outcome label of a call that returned a result, empty or not. */
    String OK = "ok";

    /**
     * Records a finished call.
     *
     * @param consumerPluginId the calling plugin
     * @param queryType the query type
     * @param outcome {@link #OK} or the stable error code
     * @param duration how long the call took
     */
    default void called(String consumerPluginId, String queryType, String outcome, Duration duration) {
    }

    /**
     * Records the state of a circuit breaker.
     *
     * @param consumerPluginId the calling plugin
     * @param queryType the query type
     * @param open whether the breaker is open
     */
    default void circuit(String consumerPluginId, String queryType, boolean open) {
    }

    /**
     * Records how many calls of a query are running.
     *
     * @param queryType the query type
     * @param delta {@code +1} when a call starts, {@code -1} when it ends
     */
    default void inFlight(String queryType, int delta) {
    }
}
