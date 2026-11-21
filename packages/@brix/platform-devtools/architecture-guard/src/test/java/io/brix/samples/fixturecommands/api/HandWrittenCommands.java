/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.api;

import io.runtime.sdk.command.CommandType;

/** A constants class written by hand, without the generator's mark, though it sits in the api package. */
public final class HandWrittenCommands {

    public static final CommandType<CancelBooking> CANCEL_BOOKING =
        CommandType.of("fixturecommands.cancel-booking", "1.0.0", CancelBooking.class);

    private HandWrittenCommands() {
    }
}
