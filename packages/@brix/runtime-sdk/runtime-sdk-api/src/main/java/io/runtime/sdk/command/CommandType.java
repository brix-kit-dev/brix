/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.command;

import java.util.Objects;
import java.util.regex.Pattern;

import io.runtime.sdk.annotation.Since;

/**
 * A typed command contract: the command's name, its contract version and its request type (root §6.3,
 * BRX-RS-MESSAGE-010, A-22).
 *
 * <p>The owner's {@code -api} artifact holds the generated constant, for example
 * {@code SampleOrdersCommands.CANCEL_ORDER}; a sender passes it to
 * {@link io.runtime.sdk.capability.CommandCapability#client(CommandType)} and never names a command as a string.</p>
 *
 * @param <C> the request type
 * @since 3.3.0
 */
@Since("3.3.0")
public final class CommandType<C> {

    /** Command names: letters, digits, dots, underscores and hyphens, as message topics allow them. */
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private static final Pattern VERSION = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

    private final String commandType;
    private final String version;
    private final Class<C> requestType;

    private CommandType(String commandType, String version, Class<C> requestType) {
        this.commandType = requireName(commandType);
        this.version = requireVersion(version);
        this.requestType = Objects.requireNonNull(requestType, "requestType must not be null");
    }

    /**
     * Defines a command.
     *
     * @param commandType the command's name
     * @param version the contract version, {@code major.minor.patch}
     * @param requestType the generated request type
     * @param <C> request type
     * @return the command type
     */
    public static <C> CommandType<C> of(String commandType, String version, Class<C> requestType) {
        return new CommandType<>(commandType, version, requestType);
    }

    /**
     * Returns the command's name.
     *
     * @return the name, such as {@code sample-orders.cancel-order}
     */
    public String commandType() {
        return commandType;
    }

    /**
     * Returns the contract version the sender's generated type was built from.
     *
     * @return {@code major.minor.patch}
     */
    public String version() {
        return version;
    }

    /**
     * Returns the contract's major version.
     *
     * @return the major version
     */
    public int major() {
        return Integer.parseInt(version.substring(0, version.indexOf('.')));
    }

    /**
     * Returns the request type.
     *
     * @return the generated request type
     */
    public Class<C> requestType() {
        return requestType;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CommandType<?> that
            && commandType.equals(that.commandType)
            && version.equals(that.version)
            && requestType.equals(that.requestType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandType, version, requestType);
    }

    @Override
    public String toString() {
        return commandType + "@" + version;
    }

    private static String requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("commandType must match " + NAME.pattern() + ": " + name);
        }
        return name;
    }

    private static String requireVersion(String version) {
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("version must be major.minor.patch: " + version);
        }
        return version;
    }
}
