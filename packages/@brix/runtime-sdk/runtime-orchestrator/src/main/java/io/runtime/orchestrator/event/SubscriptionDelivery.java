/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxRelayPolicy;

/**
 * Delivers the records of one subscription and decides what happens to each (Kafka design §4.2, D20).
 * <p>A transport hands every record to {@link #receive} in order per partition key, with the number of the
 * attempt. A delivered record is done. A permanent failure, or any failure on the last attempt, parks the
 * record: the transport keeps it in the subscription's parking place, reports it through {@link #parked},
 * and goes on. Any other failure keeps the record where it is: the transport delivers it again after the
 * delay, before any later record of its partition. The attempts, backoff and jitter are the relay's
 * (root BRX-RS-MESSAGE-015, MESSAGE-028).</p>
 */
public final class SubscriptionDelivery {

    /** A record that is not an event envelope; it is parked at once. */
    public static final String ENVELOPE_INVALID = "EVENT_ENVELOPE_INVALID";
    /** A failure the delivery core did not classify; it is retried. */
    static final String UNEXPECTED = "EVENT_UNEXPECTED";

    private static final Logger log = LoggerFactory.getLogger(SubscriptionDelivery.class);

    private final ConsumedSubscription subscription;
    private final EventDelivery delivery;
    private final OutboxRelayPolicy policy;
    private final IntFunction<Duration> jitter;
    private final EventSubscriptionMetrics metrics;

    /**
     * Creates the delivery of one subscription with random jitter.
     *
     * @param subscription the subscription
     * @param delivery the delivery core
     * @param policy attempts, backoff and jitter
     * @param metrics the subscription's metrics
     */
    public SubscriptionDelivery(
            ConsumedSubscription subscription,
            EventDelivery delivery,
            OutboxRelayPolicy policy,
            EventSubscriptionMetrics metrics) {
        this(subscription, delivery, policy, attempt -> randomJitter(policy.retryJitterMax()), metrics);
    }

    /**
     * Creates the delivery of one subscription.
     *
     * @param subscription the subscription
     * @param delivery the delivery core
     * @param policy attempts, backoff and jitter
     * @param jitter jitter added to each retry delay, by attempt
     * @param metrics the subscription's metrics
     */
    public SubscriptionDelivery(
            ConsumedSubscription subscription,
            EventDelivery delivery,
            OutboxRelayPolicy policy,
            IntFunction<Duration> jitter,
            EventSubscriptionMetrics metrics) {
        this.subscription = Objects.requireNonNull(subscription, "subscription must not be null");
        this.delivery = Objects.requireNonNull(delivery, "delivery must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.jitter = Objects.requireNonNull(jitter, "jitter must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public ConsumedSubscription subscription() {
        return subscription;
    }

    public EventSubscriptionMetrics metrics() {
        return metrics;
    }

    /**
     * Delivers one record to the subscriber.
     *
     * @param message the event
     * @param attempt this delivery's attempt, from 1
     * @return what the transport does with the record
     */
    public Outcome receive(CanonicalOutboxMessage message, int attempt) {
        try {
            delivery.deliverTo(subscription.pluginId(), subscription.handlerId(), attempt(message, attempt));
            metrics.recordDelivered(message.messageType());
            return Outcome.DELIVERED;
        } catch (EventDeliveryException failure) {
            return failed(message, failure.errorCode(), failure.permanent(), attempt);
        } catch (RuntimeException failure) {
            log.warn("event=event.delivery_failed subscription={} type={} event_id={} code={} attempt={}",
                subscription.name(), message.messageType(), message.eventId(), UNEXPECTED, attempt, failure);
            return failed(message, UNEXPECTED, false, attempt);
        }
    }

    /**
     * Reports a record the transport parked.
     *
     * @param eventType the event type, or {@code null} for a record that is not an event envelope
     * @param eventId the event id, or where the broker held the record
     * @param errorCode why it was parked
     * @param attempts deliveries tried
     */
    public void parked(String eventType, String eventId, String errorCode, int attempts) {
        metrics.recordParked(eventType, errorCode);
        log.warn("event=event.parked subscription={} type={} event_id={} code={} attempts={}",
            subscription.name(), eventType, eventId, errorCode, attempts);
    }

    private Outcome failed(CanonicalOutboxMessage message, String errorCode, boolean permanent, int attempt) {
        if (permanent || attempt >= policy.maxAttempts()) {
            return Outcome.park(errorCode);
        }
        Duration delay = policy.retryBackoffBase().multipliedBy(attempt).plus(jitter.apply(attempt));
        metrics.recordRetry(message.messageType(), errorCode);
        log.info("event=event.retry_scheduled subscription={} type={} event_id={} code={} attempt={} delay_ms={}",
            subscription.name(), message.messageType(), message.eventId(), errorCode, attempt, delay.toMillis());
        return Outcome.retry(errorCode, delay);
    }

    private static CanonicalOutboxMessage attempt(CanonicalOutboxMessage message, int attempt) {
        return new CanonicalOutboxMessage(message.messageId(), message.eventId(), message.messageKind(),
            message.messageType(), message.schemaVersion(), message.reliability(), message.producerPluginId(),
            message.scope(), message.tenantId(), message.partitionKey(), message.occurredAt(), message.correlationId(),
            message.causationId(), message.traceparent(), message.tracestate(), message.payload(), Math.max(1, attempt));
    }

    private static Duration randomJitter(Duration max) {
        long upperBound = max.toMillis() + 1L;
        if (upperBound <= 1L) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(0L, upperBound));
    }

    /**
     * What the transport does with a record.
     *
     * @param kind delivered, retry or park
     * @param errorCode why it failed, or {@code null} when delivered
     * @param delay how long to wait before the retry, or zero
     */
    public record Outcome(Kind kind, String errorCode, Duration delay) {

        /** The subscriber committed the record, or had committed it before. */
        public static final Outcome DELIVERED = new Outcome(Kind.DELIVERED, null, Duration.ZERO);

        static Outcome retry(String errorCode, Duration delay) {
            return new Outcome(Kind.RETRY, errorCode, delay);
        }

        static Outcome park(String errorCode) {
            return new Outcome(Kind.PARK, errorCode, Duration.ZERO);
        }
    }

    /** What happens to a record. */
    public enum Kind {
        /** Done: move past it. */
        DELIVERED,
        /** Deliver it again after the delay, before any later record of its partition. */
        RETRY,
        /** Keep it in the subscription's parking place, then move past it. */
        PARK
    }
}
