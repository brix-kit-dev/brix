/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import io.brix.samples.fixturecommands.api.CancelBooking;
import io.brix.samples.fixturecommands.api.FixtureCommands;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.command.CommandClient;

/** A plugin that sends the command with the client of the owner's generated type: allowed. */
public final class SubmitsTypedCommand {

    private final CommandClient<CancelBooking> cancellations;

    public SubmitsTypedCommand(CommandCapability commands) {
        this.cancellations = commands.client(FixtureCommands.CANCEL_BOOKING);
    }

    public Object cancel(String bookingId) {
        return cancellations.submit(new CancelBooking(bookingId), bookingId);
    }
}
