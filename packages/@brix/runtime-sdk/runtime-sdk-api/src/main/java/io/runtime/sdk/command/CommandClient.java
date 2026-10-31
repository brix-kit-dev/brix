/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.command;

import io.runtime.sdk.annotation.Since;
import io.runtime.sdk.capability.CommandSubmission;

/**
 * Submits commands of one {@link CommandType} to their owner (root §6.3, BRX-RS-MESSAGE-010 to MESSAGE-013).
 *
 * <p>The runtime writes the command to the sender's own outbox, in the sender's unit of work when one is open; the
 * durable receipt completes when that transaction commits. The tenant, the issuing plugin and, for an owner that
 * accepts commands only on behalf of a user, that user come from the current call; the sender never states them.
 * The owner applies a command at most once per idempotency key, for that sender and tenant.</p>
 *
 * @param <C> the request type
 * @since 3.3.0
 */
@Since("3.3.0")
public interface CommandClient<C> {

    /**
     * Submits a command.
     *
     * @param command the request
     * @param idempotencyKey the business key the owner deduplicates by, such as an order id
     * @return the command id and its durable receipt
     * @throws io.runtime.sdk.capability.CommandSubmitException when the command cannot be queued: the owner is
     *     missing, the caller may not send it on behalf of the current user, or there is no unit of work to join
     */
    default CommandSubmission submit(C command, String idempotencyKey) {
        return submit(command, idempotencyKey, idempotencyKey);
    }

    /**
     * Submits a command with an ordering key: commands with the same key reach the owner in submission order.
     *
     * @param command the request
     * @param idempotencyKey the business key the owner deduplicates by
     * @param partitionKey the ordering key
     * @return the command id and its durable receipt
     * @throws io.runtime.sdk.capability.CommandSubmitException when the command cannot be queued
     */
    CommandSubmission submit(C command, String idempotencyKey, String partitionKey);
}
