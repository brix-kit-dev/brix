/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Broker-neutral L2B port for claiming and finalizing canonical outbox records.
 *
 * <p>Implementations are custodian adapters for Data Owner outbox tables. They
 * must not read Owner business tables and must enforce ownership of an active
 * claim when finalizing, retrying, or parking a message.</p>
 *
 * <p>The {@code relayOwner} argument identifies one claim: the relay passes a token of its own for every
 * pass ({@code <relay>:<uuid>}), so a relay finalizes only what it claimed in that pass, and several relays
 * may share one outbox.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface OutboxMessageStore {

    /**
     * Appends a canonical message in the caller's active Owner transaction.
     *
     * @param message canonical message to append
     */
    void append(CanonicalOutboxMessage message);

    /**
     * Atomically claims due messages, in the order the outbox recorded them.
     * <p>A message whose partition key has an earlier message that waits for its retry or is in flight is not
     * claimed: it stays behind that message, so the events of one key leave in their order (root
     * BRX-RS-MESSAGE-027). Due messages of one key are claimed together, in order. This holds when several
     * relays claim from the same outbox at once.</p>
     *
     * @param relayOwner the claim token of this pass
     * @param now claim timestamp
     * @param leaseDuration lease duration
     * @param batchSize maximum number of messages
     * @return claimed messages
     */
    List<CanonicalOutboxMessage> claimDue(
            String relayOwner,
            OffsetDateTime now,
            Duration leaseDuration,
            int batchSize);

    /**
     * Marks a claimed message as published.
     *
     * @param relayOwner the claim token the message was claimed under
     * @param messageId canonical message id
     * @param publishedAt publish timestamp
     */
    void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt);

    /**
     * Releases a claimed message for a future retry.
     *
     * @param relayOwner the claim token the message was claimed under
     * @param messageId canonical message id
     * @param availableAt next retry timestamp
     * @param errorCode stable error code
     */
    void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode);

    /**
     * Hands a claimed message back untried, because an earlier message with its partition key failed in the
     * same pass: it waits for that message. The claim's attempt is not counted.
     *
     * @param relayOwner the claim token the message was claimed under
     * @param messageId canonical message id
     */
    void releaseUntried(String relayOwner, String messageId);

    /**
     * Parks a claimed message after retry exhaustion or permanent failure.
     *
     * @param relayOwner the claim token the message was claimed under
     * @param messageId canonical message id
     * @param errorCode stable error code
     * @param parkedAt parking timestamp
     */
    void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt);

    /**
     * Reads low-cardinality backlog state for metrics and readiness.
     *
     * @param now observation timestamp
     * @return backlog snapshot
     */
    OutboxBacklogSnapshot readBacklog(OffsetDateTime now);

    /**
     * Lists parked messages, the earliest parked first, without their payloads.
     *
     * @param limit maximum number of messages
     * @return parked messages
     */
    List<ParkedOutboxMessage> listParked(int limit);

    /**
     * Makes a parked message due again with a fresh attempt budget. Its event id does not change, so a
     * subscriber that had already committed it skips it through its inbox.
     *
     * @param eventId event id of the parked message
     * @param now replay timestamp
     * @return whether a parked message with that event id was found
     */
    boolean replayParked(String eventId, OffsetDateTime now);

    /**
     * Returns where the message with this event id stands (Kafka design §14 K14, K16).
     *
     * @param eventId the event or command id
     * @return its status and publication time, or empty when the outbox does not hold it
     */
    default Optional<OutboxMessageState> find(String eventId) {
        return Optional.empty();
    }

    /**
     * Discards a parked message: it stays as DISCARDED until the retention removes it, and is never sent (D33).
     *
     * @param eventId the parked message's event id
     * @param now discard time
     * @return whether a parked message was discarded
     */
    default boolean discardParked(String eventId, OffsetDateTime now) {
        throw new UnsupportedOperationException("This outbox store cannot discard messages");
    }

    /**
     * Makes a published message due again, with its event id and a fresh attempt budget, for example after the broker
     * lost it or for a subscription that started later (Kafka design §9, D32, D34, §14 K16). The subscribers' inboxes
     * skip it where it already arrived, as long as it was published within the outbox retention.
     *
     * @param eventId the published message's event id
     * @param now republish time
     * @param publishedSince the message must have been published at or after this
     * @return whether a published message was made due again
     */
    default boolean republish(String eventId, OffsetDateTime now, OffsetDateTime publishedSince) {
        throw new UnsupportedOperationException("This outbox store cannot republish messages");
    }

    /**
     * Deletes published and discarded messages older than the cutoff (root BRX-RS-MESSAGE-026; Kafka design D34).
     *
     * @param before messages published or discarded before this go
     * @param limit at most this many
     * @return how many were deleted
     */
    default int purge(OffsetDateTime before, int limit) {
        return 0;
    }
}
