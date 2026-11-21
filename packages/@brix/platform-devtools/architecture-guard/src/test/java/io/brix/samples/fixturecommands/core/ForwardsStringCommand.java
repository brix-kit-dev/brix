/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import io.runtime.sdk.capability.CommandCapability;

/**
 * A plugin that submits string commands another class built: forbidden. Its own code never names
 * {@code CommandRequest}; only the call to the string submit gives it away.
 */
public final class ForwardsStringCommand {

    private final CommandCapability commands;

    private final PendingCommands pending;

    public ForwardsStringCommand(CommandCapability commands, PendingCommands pending) {
        this.commands = commands;
        this.pending = pending;
    }

    public Object forward() {
        return commands.submit(pending.next());
    }
}
