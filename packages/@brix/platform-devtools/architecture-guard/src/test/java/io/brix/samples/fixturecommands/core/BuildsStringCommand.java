/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import io.brix.samples.fixturecommands.api.CancelBooking;
import io.runtime.sdk.command.CommandRequest;

/** A plugin that only builds a string command, for another class to submit: forbidden. */
public final class BuildsStringCommand {

    public Object cancellation(String bookingId) {
        return CommandRequest.of("fixturecommands.cancel-booking", new CancelBooking(bookingId), "booking", bookingId);
    }
}
