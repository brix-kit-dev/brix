/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The answers the runtime replays for endpoints that declare {@code idempotency.mode: required} (root
 * BRX-RS-MANIFEST-018; standard-link plan point 10a; idempotency design D70): one row per {@link Key} in the owner's
 * {@code {storageId}_endpoint_idempotency} table, written in the transaction of the call's business write. A call that
 * rolls back leaves no row, so it can be tried again.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface OwnerEndpointIdempotency {

    /**
     * Takes a key for a call in the owner's current transaction. A call whose key another transaction is taking does
     * not wait for it (design D69, D77).
     *
     * @param ownerId the module that owns the endpoint
     * @param key the key
     * @param fingerprint the fingerprint of the request
     * @param tenantId the tenant of the call, or {@code null} when it has none
     * @param traceId the call's trace
     * @param retention how long the row is kept
     * @return what came of it
     */
    Reservation reserve(String ownerId, Key key, String fingerprint, Long tenantId, String traceId, Duration retention);

    /**
     * Keeps the answer of the call that took a key: in the same transaction for a plugin's endpoint, and in a short one
     * of its own for an operational endpoint, whose handler runs in no transaction.
     *
     * @param ownerId the module that holds the key
     * @param key the key
     * @param status the status of the answer
     * @param body the answer's body as JSON, or {@code null} when it has none
     */
    void complete(String ownerId, Key key, int status, String body);

    /**
     * Keeps the result of the write of an operational endpoint's call, in the transaction that took the key (idempotency
     * design D80).
     *
     * @param ownerId the module that holds the key
     * @param key the key
     * @param result the write's result as JSON, or {@code null} when it has none
     */
    void keepWriteResult(String ownerId, Key key, String result);

    /**
     * Reads who holds a key, before a call runs its handler (root BRX-RS-MANIFEST-022; idempotency design D81).
     *
     * @param ownerId the module that holds the key
     * @param key the key
     * @return the call that holds it, if a call does
     */
    Optional<Held> find(String ownerId, Key key);

    /**
     * Returns the tables the retention cleans.
     *
     * @return the owners' endpoint idempotency tables
     */
    default List<String> tables() {
        return List.of();
    }

    /**
     * Deletes up to {@code limit} rows whose retention ended, in a transaction of its own (root BRX-RS-MESSAGE-026).
     *
     * @param table one of {@link #tables()}
     * @param expiredBefore rows that expired before this go
     * @param limit at most this many
     * @return how many were deleted
     */
    default int purge(String table, OffsetDateTime expiredBefore, int limit) {
        return 0;
    }

    /**
     * What a call is deduplicated by.
     *
     * @param entryId the endpoint, as {@code <module>/<handlerId>}
     * @param scope who the key belongs to, made of the tenant and the caller
     * @param key the caller's key
     */
    record Key(String entryId, String scope, String key) {

        /**
         * Creates a key.
         */
        public Key {
            Objects.requireNonNull(entryId, "entryId must not be null");
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(key, "key must not be null");
        }
    }

    /**
     * What came of taking a key.
     *
     * @param state what came of it
     * @param held the call that holds the key, for {@link State#HELD}; otherwise {@code null}
     */
    record Reservation(State state, Held held) {

        /** This call took the key. */
        public static final Reservation TAKEN = new Reservation(State.TAKEN, null);
        /** Another transaction is taking the key now. */
        public static final Reservation IN_PROGRESS = new Reservation(State.IN_PROGRESS, null);

        /**
         * Returns the reservation of a key an earlier call holds.
         *
         * @param held that call
         * @return the reservation
         */
        public static Reservation held(Held held) {
            return new Reservation(State.HELD, Objects.requireNonNull(held, "held must not be null"));
        }

        /** What came of taking a key. */
        public enum State {
            /** This call took the key, and goes on to the handler. */
            TAKEN,
            /** Another call with the key has not finished. */
            IN_PROGRESS,
            /** An earlier call succeeded and holds the key. */
            HELD
        }
    }

    /**
     * The call that holds a key.
     *
     * @param fingerprint the fingerprint of its request
     * @param status the status of its answer, or {@code null} while the answer is not kept
     * @param body its answer's body as JSON, or {@code null} when it had none
     * @param traceId its trace, or {@code null}
     * @param writeResult the result of its write through an internal contract as JSON, or {@code null}
     */
    record Held(String fingerprint, Integer status, String body, String traceId, String writeResult) {

        /**
         * Returns whether the call's answer is kept. An operational endpoint's call that stopped after its owner committed
         * holds its key without one.
         *
         * @return {@code true} when the answer can be given again
         */
        public boolean answered() {
            return status != null;
        }
    }
}
