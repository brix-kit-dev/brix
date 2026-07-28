/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

/**
 * L2B managed relay for committed canonical outbox messages.
 *
 * <p>The relay claims due messages with a lease, publishes them through a
 * broker-neutral transport, then finalizes, retries, or parks each message.
 * It does not inspect Owner business tables and does not expose an API to
 * plugins.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class OutboxRelay implements AutoCloseable {

    private static final String ERROR_INVALID_ENVELOPE = "OUTBOX_INVALID_ENVELOPE";
    private static final String ERROR_UNEXPECTED = "OUTBOX_UNEXPECTED";

    private final String relayOwner;
    private final OutboxMessageStore store;
    private final OutboxTransport transport;
    private final OutboxRelayPolicy policy;
    private final Clock clock;
    private final IntFunction<Duration> jitter;
    private final OutboxRelayMetrics metrics;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /**
     * Creates a relay with default jitter and no-op metrics.
     */
    public OutboxRelay(
            String relayOwner,
            OutboxMessageStore store,
            OutboxTransport transport,
            OutboxRelayPolicy policy,
            Clock clock) {
        this(relayOwner, store, transport, policy, clock,
            ignored -> randomJitter(policy.retryJitterMax()),
            OutboxRelayMetrics.NOOP);
    }

    /**
     * Creates a relay.
     */
    public OutboxRelay(
            String relayOwner,
            OutboxMessageStore store,
            OutboxTransport transport,
            OutboxRelayPolicy policy,
            Clock clock,
            IntFunction<Duration> jitter,
            OutboxRelayMetrics metrics) {
        if (relayOwner == null || relayOwner.isBlank()) {
            throw new IllegalArgumentException("relayOwner must not be blank");
        }
        this.relayOwner = relayOwner;
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jitter = Objects.requireNonNull(jitter, "jitter must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Claims and processes one due batch.
     *
     * @return batch result
     */
    public OutboxRelayRunResult processDueBatch() {
        if (!accepting.get()) {
            return new OutboxRelayRunResult(0, 0, 0, 0);
        }
        OffsetDateTime now = now();
        OutboxBacklogSnapshot backlog = store.readBacklog(now);
        metrics.recordBacklog(backlog);
        List<CanonicalOutboxMessage> messages = store.claimDue(
            relayOwner,
            now,
            policy.leaseDuration(),
            policy.batchSize());
        metrics.recordClaimed(messages.size());

        int published = 0;
        int retried = 0;
        int parked = 0;
        for (CanonicalOutboxMessage message : messages) {
            try {
                validateEnvelope(message);
                transport.publish(message);
                store.markPublished(relayOwner, message.messageId(), now());
                metrics.recordPublished(message.messageType());
                published++;
            } catch (OutboxTransportException ex) {
                if (shouldPark(message, ex)) {
                    store.park(relayOwner, message.messageId(), ex.errorCode(), now());
                    metrics.recordParked(message.messageType(), ex.errorCode());
                    parked++;
                } else {
                    store.releaseForRetry(relayOwner, message.messageId(), nextAvailableAt(message), ex.errorCode());
                    metrics.recordRetry(message.messageType(), ex.errorCode());
                    retried++;
                }
            } catch (RuntimeException ex) {
                OutboxTransportException wrapped = new OutboxTransportException(
                    ERROR_UNEXPECTED,
                    "Unexpected relay failure",
                    ex);
                if (shouldPark(message, wrapped)) {
                    store.park(relayOwner, message.messageId(), wrapped.errorCode(), now());
                    metrics.recordParked(message.messageType(), wrapped.errorCode());
                    parked++;
                } else {
                    store.releaseForRetry(relayOwner, message.messageId(), nextAvailableAt(message), wrapped.errorCode());
                    metrics.recordRetry(message.messageType(), wrapped.errorCode());
                    retried++;
                }
            }
        }
        return new OutboxRelayRunResult(messages.size(), published, retried, parked);
    }

    /**
     * Stops admitting new polling passes. In-flight processing finishes in the
     * caller thread before this method returns.
     */
    public void drain() {
        accepting.set(false);
    }

    @Override
    public void close() {
        drain();
    }

    private void validateEnvelope(CanonicalOutboxMessage message) {
        if ("TENANT".equals(message.scope()) && message.tenantId() == null) {
            throw new OutboxTransportException(
                ERROR_INVALID_ENVELOPE,
                "TENANT scoped message requires tenantId",
                null,
                true);
        }
        if ("PLATFORM".equals(message.scope()) && message.tenantId() != null) {
            throw new OutboxTransportException(
                ERROR_INVALID_ENVELOPE,
                "PLATFORM scoped message must not carry tenantId",
                null,
                true);
        }
    }

    private boolean shouldPark(CanonicalOutboxMessage message, OutboxTransportException ex) {
        return ex.permanent() || message.attemptCount() >= policy.maxAttempts();
    }

    private OffsetDateTime nextAvailableAt(CanonicalOutboxMessage message) {
        long multiplier = Math.max(1L, message.attemptCount());
        Duration base = policy.retryBackoffBase().multipliedBy(multiplier);
        return now().plus(base).plus(jitter.apply(message.attemptCount()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static Duration randomJitter(Duration max) {
        long upperBound = max.toMillis() + 1L;
        if (upperBound <= 1L) {
            return Duration.ZERO;
        }
        long millis = ThreadLocalRandom.current().nextLong(0L, upperBound);
        return Duration.ofMillis(millis);
    }
}
