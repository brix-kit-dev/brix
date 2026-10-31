/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.command.CommandClient;
import io.runtime.sdk.command.CommandRequest;
import io.runtime.sdk.command.CommandType;

/**
 * Submits a typed cross-plugin command (root BRX-RS-MESSAGE-010 to MESSAGE-016).
 *
 * <p>The command is written to the submitting plugin's own outbox: inside its unit of work it commits or
 * rolls back with it; outside one the runtime writes it in a transaction of its own. The plugin names the
 * command type it declares under {@code commands.consumes}, the payload and the business idempotency key;
 * the runtime builds the envelope (command id, producer, tenant, correlation, trace) and delivers the command
 * to the one plugin that provides it, exactly once per idempotency key.</p>
 *
 * <p>The returned {@link CommandSubmission#durableReceipt()} completes when the command is durably queued:
 * after the surrounding unit of work commits, or exceptionally when it rolls back. It says nothing about the
 * provider accepting or completing the command; the provider reports that through its own events.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface CommandCapability {

    /**
     * Returns the client of a command this plugin declares under {@code commands.consumes} (root A-22): requests are
     * the owner's generated type, and the tenant, issuer and user come from the current call.
     *
     * @param type the generated command type, from the owner's {@code -api} artifact
     * @param <C> request type
     * @return the client
     * @throws CommandSubmitException {@code command.undeclared} when the plugin does not declare the command, or
     *     {@code command.invalid} when the type's version is outside the declared range
     * @since 3.3.0
     */
    default <C> CommandClient<C> client(CommandType<C> type) {
        throw new UnsupportedOperationException(
            getClass().getName() + " has no typed command clients; the Runtime's CommandCapability has");
    }

    /**
     * Submits a command named by a string, with a payload of any type and a tenant the caller states.
     *
     * @param request the command
     * @param <C> payload type
     * @return the command id and its durable receipt
     * @throws CommandSubmitException when the command cannot be queued, for example an undeclared command type
     * @deprecated since 3.3.0, for removal in 4.0.0: use {@link #client(CommandType)} with the owner's generated
     *     {@link CommandType} (root A-22)
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    <C> CommandSubmission submit(CommandRequest<C> request);
}
