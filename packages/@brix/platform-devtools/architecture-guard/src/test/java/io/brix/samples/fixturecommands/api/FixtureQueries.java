/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.api;

import java.util.Optional;

import io.runtime.sdk.query.GeneratedContractConstants;
import io.runtime.sdk.query.QueryType;

/** Stands for the generated constants of the queries a plugin provides, with the generator's mark. */
@GeneratedContractConstants
public final class FixtureQueries {

    public static final QueryType<FindBooking, Booking> GET_BOOKING =
        QueryType.of("fixturecommands.get-booking", "1.0.0", FindBooking.class, Booking.class);

    public static final QueryType<FindBooking, Optional<Booking>> FIND_BOOKING =
        QueryType.optional("fixturecommands.find-booking", "1.0.0", FindBooking.class, Booking.class);

    private FixtureQueries() {
    }
}
