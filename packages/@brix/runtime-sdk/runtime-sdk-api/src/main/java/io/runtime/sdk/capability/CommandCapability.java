/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.util.concurrent.CompletionStage;

import io.runtime.sdk.command.CommandEnvelope;

/**
 * Capability contract for typed asynchronous command submission.
 *
 * <p>Plugins submit versioned command envelopes and receive a durable sender
 * receipt only after the sender canonical outbox record has committed. Handler
 * routing, outbox, relay, inbox and broker details remain Runtime Shell
 * internals.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface CommandCapability {

    /**
     * Submits a typed command for reliable asynchronous delivery.
     *
     * @param command command envelope
     * @param <C> command payload type
     * @return stage completed with a durable receipt after sender commit
     */
    <C> CompletionStage<CommandReceipt> submit(CommandEnvelope<C> command);
}
