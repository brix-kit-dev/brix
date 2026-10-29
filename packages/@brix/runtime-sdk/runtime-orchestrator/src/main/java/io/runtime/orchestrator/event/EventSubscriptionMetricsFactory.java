/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * Creates the metrics of one subscription a broker consumes for.
 */
@FunctionalInterface
public interface EventSubscriptionMetricsFactory {

    /** Records nothing. */
    EventSubscriptionMetricsFactory NOOP = subscription -> EventSubscriptionMetrics.NOOP;

    /**
     * Returns the metrics of one subscription.
     *
     * @param subscription the subscription's name
     * @return its metrics
     */
    EventSubscriptionMetrics forSubscription(String subscription);
}
