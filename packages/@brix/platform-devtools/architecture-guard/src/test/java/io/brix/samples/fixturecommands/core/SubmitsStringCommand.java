/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import io.brix.samples.fixturecommands.api.CancelBooking;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.command.CommandRequest;

/** A plugin that builds a string command and submits it: forbidden (root A-22). */
public final class SubmitsStringCommand {

    private final CommandCapability commands;

    public SubmitsStringCommand(CommandCapability commands) {
        this.commands = commands;
    }

    public Object cancel(String bookingId) {
        return commands.submit(CommandRequest.of("fixturecommands.cancel-booking", new CancelBooking(bookingId),
            "booking", bookingId));
    }
}
