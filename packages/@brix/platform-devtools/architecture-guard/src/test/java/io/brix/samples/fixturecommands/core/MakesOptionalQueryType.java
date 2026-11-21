/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import java.util.Optional;

import io.brix.samples.fixturecommands.api.Booking;
import io.brix.samples.fixturecommands.api.FindBooking;
import io.runtime.sdk.query.QueryType;

/** A plugin class that makes an optional query type of its own. */
public final class MakesOptionalQueryType {

    private MakesOptionalQueryType() {
    }

    static QueryType<FindBooking, Optional<Booking>> findBooking() {
        return QueryType.optional("fixturecommands.find-booking", "1.0.0", FindBooking.class, Booking.class);
    }
}
