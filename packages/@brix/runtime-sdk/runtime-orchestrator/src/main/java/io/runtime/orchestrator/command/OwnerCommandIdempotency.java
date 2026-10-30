/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The providers' business idempotency records (root BRX-RS-MESSAGE-014): one per
 * {@code (handlerId, idempotencyScope, idempotencyKey)} in the provider's {@code {storageId}_command_idempotency}
 * table, written in the transaction that runs the handler.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface OwnerCommandIdempotency {

    /**
     * Records the key in the provider's current transaction.
     *
     * @param ownerId the providing plugin
     * @param handlerId the handler
     * @param message the command
     * @param idempotencyScope what the key is unique within
     * @param idempotencyKey the business key
     * @return {@code false} when an earlier command already took the key, and the handler must not run again
     */
    boolean reserve(String ownerId, String handlerId, CanonicalOutboxMessage message, String idempotencyScope,
            String idempotencyKey);
}
