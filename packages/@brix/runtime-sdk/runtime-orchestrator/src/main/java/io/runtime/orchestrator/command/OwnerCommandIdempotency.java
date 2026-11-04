/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The providers' business idempotency records (root BRX-RS-MESSAGE-014): one per
 * {@code (handlerId, idempotencyScope, idempotencyKey)} in the provider's {@code {storageId}_command_idempotency}
 * table, written in the transaction that runs the handler. A command that takes no key, such as a duplicate, has
 * its outcome in the provider's {@code {storageId}_command_outcome} table instead (standard-link plan point 7); its
 * sender reads both by command id.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface OwnerCommandIdempotency {

    /**
     * Takes the key for a command in the provider's current transaction, with the fingerprint of its payload
     * (standard-link plan point 8). A command whose key another transaction is taking waits for that transaction to
     * end, as long as its own unit of work lasts, rather than failing. The key is held once the transaction commits:
     * a command whose handler fails or rejects it rolls back and leaves the key free (plan point 9).
     *
     * @param ownerId the providing plugin
     * @param handlerId the handler
     * @param message the command
     * @param idempotencyScope what the key is unique within
     * @param idempotencyKey the business key
     * @param fingerprint the fingerprint of the command's payload
     * @return {@code false} when an earlier command already took the key, and the handler must not run again
     */
    boolean reserve(String ownerId, String handlerId, CanonicalOutboxMessage message, String idempotencyScope,
            String idempotencyKey, String fingerprint);

    /**
     * Returns the command that holds a key, with the fingerprint of its payload (standard-link plan points 7, 8).
     *
     * @param ownerId the providing plugin
     * @param handlerId the handler
     * @param idempotencyScope what the key is unique within
     * @param idempotencyKey the business key
     * @return the holder; empty when no command holds the key, or the store does not tell
     */
    default Optional<Holder> holder(String ownerId, String handlerId, String idempotencyScope,
            String idempotencyKey) {
        return Optional.empty();
    }

    /**
     * Records, in the provider's current transaction, what became of a command that took no key: a duplicate, a
     * conflict, a rejection or a discard (standard-link plan point 7). A store that keeps no outcomes records
     * nothing.
     *
     * @param ownerId the providing plugin
     * @param handlerId the handler
     * @param outcome the outcome; its state is not {@code SUCCEEDED}, which the key record tells
     */
    default void recordOutcome(String ownerId, String handlerId, CommandRecord outcome) {
    }

    /**
     * Finds what a provider recorded about a command: the key it holds, read as {@code SUCCEEDED}, or its outcome
     * (standard-link plan point 7).
     *
     * @param ownerId the providing plugin
     * @param handlerId the handler
     * @param commandId the command
     * @return the record; empty when there is none, or the store does not tell
     */
    default Optional<CommandRecord> find(String ownerId, String handlerId, String commandId) {
        return Optional.empty();
    }

    /**
     * Returns the tables the retention cleans (Kafka design D34, §14 K16): the providers' command idempotency and
     * command outcome tables.
     *
     * @return the tables
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

    /**
     * The command that holds a key.
     *
     * @param commandId the command
     * @param fingerprint the fingerprint of its payload; {@code null} for a key taken before fingerprints were kept
     */
    record Holder(String commandId, String fingerprint) {
    }
}
