/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import java.util.function.Function;

import io.brix.samples.fixturecommands.api.CancelBooking;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.command.CommandRequest;

/**
 * A plugin that hands the string submit on as a method reference: forbidden. The reference's type is a local
 * variable's, which the bytecode does not keep; only the reference itself gives it away.
 */
public final class ReferencesStringSubmit {

    private final CommandCapability commands;

    public ReferencesStringSubmit(CommandCapability commands) {
        this.commands = commands;
    }

    public Object submitter() {
        Function<CommandRequest<CancelBooking>, Object> submit = commands::submit;
        return submit;
    }
}
