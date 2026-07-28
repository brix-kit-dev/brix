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

/**
 * Broker-neutral L2B port for claiming and finalizing canonical outbox records.
 *
 * <p>Implementations are custodian adapters for Data Owner outbox tables. They
 * must not read Owner business tables and must enforce ownership of an active
 * claim when finalizing, retrying, or parking a message.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface OutboxMessageStore {

    /**
     * Atomically claims due messages for a relay owner.
     *
     * @param relayOwner stable relay instance identity
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
     * @param relayOwner stable relay instance identity
     * @param messageId canonical message id
     * @param publishedAt publish timestamp
     */
    void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt);

    /**
     * Releases a claimed message for a future retry.
     *
     * @param relayOwner stable relay instance identity
     * @param messageId canonical message id
     * @param availableAt next retry timestamp
     * @param errorCode stable error code
     */
    void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode);

    /**
     * Parks a claimed message after retry exhaustion or permanent failure.
     *
     * @param relayOwner stable relay instance identity
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
}
