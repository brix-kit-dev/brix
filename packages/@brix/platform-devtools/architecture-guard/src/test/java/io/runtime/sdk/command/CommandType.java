/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.sdk.command;

/** Stand-in for the SDK's typed command contract, under its real name: the guard does not depend on the SDK. */
public final class CommandType<C> {

    private final String commandType;

    private final String version;

    private final Class<C> requestType;

    private CommandType(String commandType, String version, Class<C> requestType) {
        this.commandType = commandType;
        this.version = version;
        this.requestType = requestType;
    }

    public static <C> CommandType<C> of(String commandType, String version, Class<C> requestType) {
        return new CommandType<>(commandType, version, requestType);
    }

    public String commandType() {
        return commandType;
    }

    public String version() {
        return version;
    }

    public Class<C> requestType() {
        return requestType;
    }
}
