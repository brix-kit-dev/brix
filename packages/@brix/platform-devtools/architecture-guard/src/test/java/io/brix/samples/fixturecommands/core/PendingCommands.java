/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import io.runtime.sdk.command.CommandRequest;

/** Hands out string commands built elsewhere; {@link ForwardsStringCommand} submits them. */
public interface PendingCommands {

    CommandRequest<?> next();
}
