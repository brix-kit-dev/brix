/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.OffsetDateTime;
import java.util.List;

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

    /**
     * Returns the idempotency tables the retention cleans (Kafka design D34, §14 K16).
     *
     * @return the providers' command idempotency tables
     */
    default List<String> tables() {
        return List.of();
    }

    /**
     * Deletes up to {@code limit} records created before the cutoff from one table, in a transaction of its own;
     * a key is honoured for the inbox retention (root BRX-RS-MESSAGE-014, MESSAGE-026).
     *
     * @param table one of {@link #tables()}
     * @param createdBefore records created before this go
     * @param limit at most this many
     * @return how many were deleted
     */
    default int purge(String table, OffsetDateTime createdBefore, int limit) {
        return 0;
    }
}
