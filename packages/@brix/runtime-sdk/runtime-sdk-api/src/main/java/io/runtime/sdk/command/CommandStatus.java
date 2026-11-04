/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.command;

import java.time.Instant;

import io.runtime.sdk.annotation.Since;

/**
 * Where a command stands, as its sender reads it by command id (root BRX-RS-MESSAGE-013, MESSAGE-014;
 * standard-link plan point 7). The durable receipt only says that the command was queued; the status says whether
 * its owner ran it, and how that ended.
 *
 * <p>Only the plugin that submitted a command reads its status, and only in the tenant it was submitted in. For
 * anyone else, and once the records are past their retention (30 days by default), the command is
 * {@link State#UNKNOWN}.</p>
 *
 * @param commandId the command id, as {@link io.runtime.sdk.capability.CommandSubmission#commandId()} returned it
 * @param state where it stands
 * @param errorCode the stable error code of a rejected, conflicting or parked command; otherwise {@code null}
 * @param originalCommandId for a duplicate or a conflict, the earlier command that holds the idempotency key;
 *     otherwise {@code null}
 * @param at when the command reached this state, as far as its records tell; {@code null} when it is unknown
 * @since 3.3.0
 */
@Since("3.3.0")
public record CommandStatus(String commandId, State state, String errorCode, String originalCommandId, Instant at) {

    /**
     * The error code of a {@link State#CONFLICT}: an earlier command holds the idempotency key with other content
     * (standard-link plan point 8). Its category is {@code CONFLICT}: send the new content with a key of its own.
     */
    public static final String IDEMPOTENCY_CONFLICT = "command.idempotency_conflict";

    /** Where a command stands. */
    public enum State {
        /** The command is durable and waits for its owner, or is on its way to it. */
        QUEUED,
        /** The owner's handler finished and its transaction committed; the command holds its idempotency key. */
        SUCCEEDED,
        /** The owner's handler rejected the command; nothing changed, and the key stays free. */
        REJECTED,
        /**
         * An earlier command with the same idempotency key and the same content took effect, so this one did not run;
         * {@link #originalCommandId()} names it.
         */
        DUPLICATE,
        /**
         * An earlier command holds the idempotency key with other content, so this one did not run;
         * {@link #originalCommandId()} names it.
         */
        CONFLICT,
        /** The command could not be delivered or handled; it waits for an operator to replay or discard it. */
        PARKED,
        /** An operator discarded the parked command; it never runs. */
        DISCARDED,
        /** Nothing known: another plugin's or another tenant's command, one past retention, or no command at all. */
        UNKNOWN
    }

    /**
     * Creates a status.
     */
    public CommandStatus {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId must not be blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
    }

    /**
     * Returns the status of a command nothing is known about.
     *
     * @param commandId the command id asked about
     * @return an {@link State#UNKNOWN} status
     */
    public static CommandStatus unknown(String commandId) {
        return new CommandStatus(commandId, State.UNKNOWN, null, null, null);
    }
}
