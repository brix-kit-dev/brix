/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import java.util.List;
import java.util.Optional;

import io.brix.samples.fixturecommands.api.Booking;
import io.brix.samples.fixturecommands.api.CancelBooking;
import io.brix.samples.fixturecommands.api.FindBooking;
import io.brix.samples.fixturecommands.api.FixtureCommands;
import io.brix.samples.fixturecommands.api.FixtureQueries;
import io.runtime.sdk.command.CommandType;
import io.runtime.sdk.query.QueryType;

/**
 * A plugin class that reads the generated constants, which any class may: their accessors, and other methods named
 * {@code of} such as {@code List.of}, are not type factories.
 */
public final class ReadsContractTypes {

    private ReadsContractTypes() {
    }

    static List<String> describe() {
        CommandType<CancelBooking> command = FixtureCommands.CANCEL_BOOKING;
        QueryType<FindBooking, Optional<Booking>> query = FixtureQueries.FIND_BOOKING;
        return List.of(command.commandType(), command.version(), query.queryType(), query.version());
    }
}
