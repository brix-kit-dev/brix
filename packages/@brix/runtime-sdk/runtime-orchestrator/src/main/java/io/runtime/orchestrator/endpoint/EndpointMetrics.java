/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.time.Duration;

/**
 * What the dispatcher reports about endpoint calls (root BRX-RS-MANIFEST-022, OTEL-006; standard-link plan L7.5).
 * Labels are the module, the endpoint and the outcome only, never tenants, users or paths.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface EndpointMetrics {

    /** Records nothing. */
    EndpointMetrics NOOP = new EndpointMetrics() {
    };

    /**
     * A call ended.
     *
     * @param pluginId the module that owns the endpoint
     * @param endpointId the endpoint
     * @param outcome {@code ok}, {@code invalid}, {@code denied}, {@code not_found}, {@code conflict},
     *     {@code rejected}, {@code unavailable}, {@code deadline_exceeded} or {@code failed}
     * @param took how long the runtime took to answer, from finding the route
     */
    default void finished(String pluginId, String endpointId, String outcome, Duration took) {
    }

    /**
     * A call's handler started or ended; {@code delta} is 1 or -1.
     *
     * @param pluginId the module that owns the endpoint
     * @param endpointId the endpoint
     * @param delta the change in calls going on
     */
    default void inFlight(String pluginId, String endpointId, int delta) {
    }
}
