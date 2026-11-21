/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.api;

import io.runtime.sdk.command.CommandType;
import io.runtime.sdk.query.GeneratedContractConstants;

/** Stands for the generated constants of the commands a plugin handles, with the generator's mark. */
@GeneratedContractConstants
public final class FixtureCommands {

    public static final CommandType<CancelBooking> CANCEL_BOOKING =
        CommandType.of("fixturecommands.cancel-booking", "1.0.0", CancelBooking.class);

    private FixtureCommands() {
    }
}
