/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.command.CommandRequest;

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
     * Submits a command.
     *
     * @param request the command
     * @param <C> payload type
     * @return the command id and its durable receipt
     * @throws CommandSubmitException when the command cannot be queued, for example an undeclared command type
     */
    <C> CommandSubmission submit(CommandRequest<C> request);
}
