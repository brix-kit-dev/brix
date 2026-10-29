/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxBacklogSnapshot;
import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.orchestrator.outbox.ParkedOutboxMessage;

/**
 * An owner outbox in memory, with the claim, lease, retry and park rules of the JDBC store: rows are claimed
 * in the order they occurred, a row waits behind an earlier row of its partition key that waits for its retry or
 * is in flight, a claim counts an attempt, an expired lease makes the row claimable again, and only the claimant
 * may finish it.
 */
public final class InMemoryOutboxStore implements OutboxMessageStore {

    private enum Status { PENDING, IN_FLIGHT, PUBLISHED, PARKED }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public synchronized void append(CanonicalOutboxMessage message) {
        rows.add(new Row(message));
    }

    @Override
    public synchronized List<CanonicalOutboxMessage> claimDue(
            String relayOwner, OffsetDateTime now, Duration leaseDuration, int batchSize) {
        // As the JDBC store: the candidates come from the rows as they are before this claim.
        List<Row> candidates = new ArrayList<>();
        for (Row row : rows) {
            if (candidates.size() == batchSize) {
                break;
            }
            boolean due = row.status == Status.PENDING && !row.availableAt.isAfter(now);
            boolean expired = row.status == Status.IN_FLIGHT && row.claimUntil.isBefore(now);
            if ((due || expired) && !waitsBehind(row, now)) {
                candidates.add(row);
            }
        }
        List<CanonicalOutboxMessage> claimed = new ArrayList<>();
        for (Row row : candidates) {
            row.status = Status.IN_FLIGHT;
            row.claimedBy = relayOwner;
            row.claimUntil = now.plus(leaseDuration);
            row.attempts++;
            claimed.add(row.claimed());
        }
        return claimed;
    }

    @Override
    public synchronized void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
        claimedBy(relayOwner, messageId).status = Status.PUBLISHED;
    }

    @Override
    public synchronized void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
        Row row = claimedBy(relayOwner, messageId);
        row.status = Status.PENDING;
        row.availableAt = availableAt;
        row.errorCode = errorCode;
    }

    @Override
    public synchronized void releaseUntried(String relayOwner, String messageId) {
        Row row = claimedBy(relayOwner, messageId);
        row.status = Status.PENDING;
        row.claimedBy = null;
        row.attempts = Math.max(0, row.attempts - 1);
    }

    @Override
    public synchronized void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
        Row row = claimedBy(relayOwner, messageId);
        row.status = Status.PARKED;
        row.errorCode = errorCode;
        row.availableAt = parkedAt;
    }

    @Override
    public synchronized OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
        long pending = rows.stream().filter(row -> row.status == Status.PENDING).count();
        long inFlight = rows.stream().filter(row -> row.status == Status.IN_FLIGHT).count();
        long parked = rows.stream().filter(row -> row.status == Status.PARKED).count();
        Duration oldest = rows.stream()
            .filter(row -> row.status == Status.PENDING)
            .map(row -> Duration.between(row.message.occurredAt(), now))
            .max(Duration::compareTo)
            .orElse(Duration.ZERO);
        return new OutboxBacklogSnapshot(pending, inFlight, parked, oldest);
    }

    @Override
    public synchronized List<ParkedOutboxMessage> listParked(int limit) {
        return rows.stream()
            .filter(row -> row.status == Status.PARKED)
            .limit(limit)
            .map(row -> new ParkedOutboxMessage(row.message.eventId(), row.message.messageId(), row.message.messageType(),
                row.message.schemaVersion(), row.message.producerPluginId(), row.message.tenantId(), row.attempts,
                row.errorCode, row.message.occurredAt(), row.availableAt))
            .toList();
    }

    @Override
    public synchronized boolean replayParked(String eventId, OffsetDateTime now) {
        for (Row row : rows) {
            if (row.status == Status.PARKED && row.message.eventId().equals(eventId)) {
                row.status = Status.PENDING;
                row.availableAt = now;
                row.attempts = 0;
                return true;
            }
        }
        return false;
    }

    /**
     * Returns how often the rows of an event were claimed.
     *
     * @param eventId the event id
     * @return the attempts of its rows
     */
    public synchronized int attempts(String eventId) {
        return rows.stream().filter(row -> row.message.eventId().equals(eventId)).mapToInt(row -> row.attempts).sum();
    }

    /** Whether an earlier row of the same partition key waits for its retry or is in flight. */
    private boolean waitsBehind(Row row, OffsetDateTime now) {
        for (Row earlier : rows) {
            if (earlier == row) {
                return false;
            }
            boolean sameKey = earlier.message.partitionKey().equals(row.message.partitionKey());
            boolean waiting = earlier.status == Status.PENDING && earlier.availableAt.isAfter(now);
            boolean inFlight = earlier.status == Status.IN_FLIGHT && !earlier.claimUntil.isBefore(now);
            if (sameKey && (waiting || inFlight)) {
                return true;
            }
        }
        return false;
    }

    private Row claimedBy(String relayOwner, String messageId) {
        for (Row row : rows) {
            if (row.message.messageId().equals(messageId)) {
                if (row.status != Status.IN_FLIGHT || !relayOwner.equals(row.claimedBy)) {
                    throw new IllegalStateException("the claim of " + messageId + " was lost");
                }
                return row;
            }
        }
        throw new IllegalStateException("no outbox row " + messageId);
    }

    private static final class Row {

        private final CanonicalOutboxMessage message;
        private Status status = Status.PENDING;
        private OffsetDateTime availableAt = OffsetDateTime.MIN;
        private OffsetDateTime claimUntil;
        private String claimedBy;
        private String errorCode;
        private int attempts;

        private Row(CanonicalOutboxMessage message) {
            this.message = message;
        }

        private CanonicalOutboxMessage claimed() {
            return new CanonicalOutboxMessage(message.messageId(), message.eventId(), message.messageKind(),
                message.messageType(), message.schemaVersion(), message.reliability(), message.producerPluginId(),
                message.scope(), message.tenantId(), message.partitionKey(), message.occurredAt(), message.correlationId(),
                message.causationId(), message.traceparent(), message.tracestate(), message.payload(), attempts);
        }
    }
}
