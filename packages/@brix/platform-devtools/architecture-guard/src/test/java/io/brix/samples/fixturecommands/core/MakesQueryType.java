/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import io.brix.samples.fixturecommands.api.Booking;
import io.brix.samples.fixturecommands.api.FindBooking;
import io.runtime.sdk.query.QueryType;

/** A plugin class that makes a query type of its own instead of taking the provider's generated constant. */
public final class MakesQueryType {

    private MakesQueryType() {
    }

    static QueryType<FindBooking, Booking> getBooking() {
        return QueryType.of("fixturecommands.get-booking", "1.0.0", FindBooking.class, Booking.class);
    }
}
