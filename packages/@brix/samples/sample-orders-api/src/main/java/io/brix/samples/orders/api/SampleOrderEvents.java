/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The events sample-orders publishes, as its manifest declares them under {@code events.publishes}. A subscriber names
 * the publisher in its subscription as {@code publisherPolicyRef: sample-orders-owner}.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class SampleOrderEvents {

    /** The publishing plugin. */
    public static final String PUBLISHER = "sample-orders";

    /** An order was placed: STANDARD, through the outbox; payload {@link SampleOrderPlacedPayload}. */
    public static final String PLACED = "SampleOrderPlaced";

    /** Something happened to an order: BEST_EFFORT; payload {@link SampleOrderActivityPayload}. */
    public static final String ACTIVITY = "SampleOrderActivity";

    private SampleOrderEvents() {
    }
}
