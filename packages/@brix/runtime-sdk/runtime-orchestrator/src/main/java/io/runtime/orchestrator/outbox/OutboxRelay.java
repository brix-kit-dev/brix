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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

import io.runtime.orchestrator.tracing.MessageTracing;

/**
 * L2B managed relay for committed canonical outbox messages.
 *
 * <p>The relay claims due messages with a lease, publishes them through a
 * broker-neutral transport, then finalizes, retries, or parks each message.
 * It does not inspect Owner business tables and does not expose an API to
 * plugins.</p>
 *
 * <p>Every pass claims under a token of its own ({@code <relayOwner>:<uuid>}), so relays on several
 * hosts can share one outbox: a relay finalizes only the rows of its own claim, and the store hands out
 * a later message of a partition key only together with the earlier ones (root BRX-RS-MESSAGE-027).
 * When the transport stops answering in the middle of a pass, or the relay is being stopped, the rest
 * of the pass goes back untried and spends no attempt (Kafka design D21).</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class OutboxRelay implements AutoCloseable {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OutboxRelay.class);

    private static final String ERROR_INVALID_ENVELOPE = "OUTBOX_INVALID_ENVELOPE";
    private static final String ERROR_UNEXPECTED = "OUTBOX_UNEXPECTED";
    private static final int MAX_RELAY_OWNER_LENGTH = 64;

    private final String relayOwner;
    private final OutboxMessageStore store;
    private final OutboxTransport transport;
    private final OutboxRelayPolicy policy;
    private final Clock clock;
    private final IntFunction<Duration> jitter;
    private final OutboxRelayMetrics metrics;
    private final MessageTracing tracing;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private boolean waiting;

    /**
     * Creates a relay with default jitter and no-op metrics.
     */
    public OutboxRelay(
            String relayOwner,
            OutboxMessageStore store,
            OutboxTransport transport,
            OutboxRelayPolicy policy,
            Clock clock) {
        this(relayOwner, store, transport, policy, clock, OutboxRelayMetrics.NOOP);
    }

    /**
     * Creates a relay with default jitter.
     */
    public OutboxRelay(
            String relayOwner,
            OutboxMessageStore store,
            OutboxTransport transport,
            OutboxRelayPolicy policy,
            Clock clock,
            OutboxRelayMetrics metrics) {
        this(relayOwner, store, transport, policy, clock, metrics, MessageTracing.NOOP);
    }

    /**
     * Creates a relay with default jitter that sends each message in its PRODUCER span.
     */
    public OutboxRelay(
            String relayOwner,
            OutboxMessageStore store,
            OutboxTransport transport,
            OutboxRelayPolicy policy,
            Clock clock,
            OutboxRelayMetrics metrics,
            MessageTracing tracing) {
        this(relayOwner, store, transport, policy, clock,
            ignored -> randomJitter(policy.retryJitterMax()),
            metrics, tracing);
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
        this(relayOwner, store, transport, policy, clock, jitter, metrics, MessageTracing.NOOP);
    }

    /**
     * Creates a relay that sends each message in its PRODUCER span (root BRX-RS-OTEL-002).
     */
    public OutboxRelay(
            String relayOwner,
            OutboxMessageStore store,
            OutboxTransport transport,
            OutboxRelayPolicy policy,
            Clock clock,
            IntFunction<Duration> jitter,
            OutboxRelayMetrics metrics,
            MessageTracing tracing) {
        if (relayOwner == null || relayOwner.isBlank()) {
            throw new IllegalArgumentException("relayOwner must not be blank");
        }
        if (relayOwner.length() > MAX_RELAY_OWNER_LENGTH) {
            // The claim token <relayOwner>:<uuid> has to fit the canonical claim_owner column (128).
            throw new IllegalArgumentException("relayOwner must be at most " + MAX_RELAY_OWNER_LENGTH + " characters");
        }
        this.relayOwner = relayOwner;
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jitter = Objects.requireNonNull(jitter, "jitter must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.tracing = Objects.requireNonNull(tracing, "tracing must not be null");
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
        boolean ready = transport.ready();
        metrics.recordWaiting(!ready);
        if (!ready) {
            // For example the plugins have not started yet, or the broker cannot be reached: claiming now
            // would spend attempts, or park rows that only wait for their subscribers.
            if (!waiting) {
                waiting = true;
                log.info("event=outbox.relay_waiting pending={} reason=transport_not_ready", backlog.pendingCount());
            }
            return new OutboxRelayRunResult(0, 0, 0, 0);
        }
        if (waiting) {
            waiting = false;
            log.info("event=outbox.relay_resumed pending={}", backlog.pendingCount());
        }
        String claim = relayOwner + ":" + UUID.randomUUID();
        List<CanonicalOutboxMessage> messages = store.claimDue(
            claim,
            now,
            policy.leaseDuration(),
            policy.batchSize());
        metrics.recordClaimed(messages.size());
        int published = 0;
        int retried = 0;
        int parked = 0;
        // Keys whose earlier message failed in this pass: their later messages wait for it, untried.
        Set<String> heldBack = new HashSet<>();
        boolean stopped = false;
        for (CanonicalOutboxMessage message : messages) {
            if (stopped || heldBack.contains(message.partitionKey())) {
                handBack(claim, message);
                continue;
            }
            if (!accepting.get() || Thread.currentThread().isInterrupted()) {
                stopped = true;
                handBack(claim, message);
                continue;
            }
            OutboxTransportException failure = null;
            try {
                validateEnvelope(message);
                send(message);
            } catch (OutboxTransportException ex) {
                failure = ex;
            } catch (RuntimeException ex) {
                failure = new OutboxTransportException(ERROR_UNEXPECTED, "Unexpected relay failure", ex);
            }
            if (failure != null && !failure.permanent() && Thread.currentThread().isInterrupted()) {
                // The relay is being stopped: the send was cut short, not refused. The message keeps its
                // attempt and is sent again by the next pass (perhaps twice; the inboxes deduplicate).
                stopped = true;
                handBack(claim, message);
                continue;
            }
            try {
                if (failure == null) {
                    store.markPublished(claim, message.messageId(), now());
                    metrics.recordPublished(message.messageType());
                    published++;
                } else if (shouldPark(message, failure)) {
                    store.park(claim, message.messageId(), failure.errorCode(), now());
                    metrics.recordParked(message.messageType(), failure.errorCode());
                    log.warn("event=outbox.parked type={} message_id={} code={} attempts={}",
                        message.messageType(), message.messageId(), failure.errorCode(), message.attemptCount(), failure);
                    parked++;
                } else {
                    store.releaseForRetry(claim, message.messageId(), nextAvailableAt(message), failure.errorCode());
                    metrics.recordRetry(message.messageType(), failure.errorCode());
                    heldBack.add(message.partitionKey());
                    retried++;
                    if (!transport.ready()) {
                        // The transport went away during the pass: this message spent its attempt (a send
                        // timeout counts, D21), the others go back untried and the relay waits.
                        stopped = true;
                        waiting = true;
                        metrics.recordWaiting(true);
                        log.info("event=outbox.relay_waiting pending={} reason=transport_lost_during_pass",
                            messages.size() - published - parked);
                    }
                }
            } catch (RuntimeException lost) {
                // The claim was lost (the lease expired and another pass took the row): its new claimant decides.
                log.warn("event=outbox.claim_lost type={} message_id={}", message.messageType(), message.messageId(), lost);
                heldBack.add(message.partitionKey());
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

    /** Sends the message in its PRODUCER span, with the span's trace context (root BRX-RS-OTEL-002). */
    private void send(CanonicalOutboxMessage message) {
        try (MessageTracing.MessageSpan span = tracing.producer(message)) {
            try {
                transport.publish(message.withTraceContext(span.traceparent(), span.tracestate()));
            } catch (RuntimeException failure) {
                span.failed(failure);
                throw failure;
            }
        }
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
        return now().plus(policy.backoffAfter(message.attemptCount())).plus(jitter.apply(message.attemptCount()));
    }

    private void handBack(String claim, CanonicalOutboxMessage message) {
        try {
            store.releaseUntried(claim, message.messageId());
        } catch (RuntimeException lost) {
            log.warn("event=outbox.claim_lost type={} message_id={}", message.messageType(), message.messageId(), lost);
        }
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
