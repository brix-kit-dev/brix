/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixturecommands.core;

import io.brix.samples.fixturecommands.api.CancelBooking;
import io.runtime.sdk.command.CommandType;

/** A plugin class that keeps a method reference to the command type factory, to make types later. */
public final class ReferencesCommandTypeFactory {

    /** Makes a command type from its name, version and request type. */
    interface Factory {

        CommandType<CancelBooking> make(String commandType, String version, Class<CancelBooking> requestType);
    }

    static final Factory FACTORY = CommandType::of;

    private ReferencesCommandTypeFactory() {
    }
}
