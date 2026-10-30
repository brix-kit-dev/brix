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
    /** The subscriber, or its database, cannot take the record now; it waits without spending an attempt. */
    public static final String OWNER_UNAVAILABLE = "EVENT_OWNER_UNAVAILABLE";
    private static final Duration MIN_WAIT = Duration.ofSeconds(1);
    private static final java.util.Set<String> NOT_READY = java.util.Set.of(EventDelivery.SUBSCRIBER_NOT_READY,
        io.runtime.orchestrator.command.CommandDelivery.PROVIDER_NOT_READY);
    private static final java.util.Set<String> UNAVAILABLE_TYPES = java.util.Set.of(
        "org.springframework.transaction.CannotCreateTransactionException",
        "org.springframework.dao.DataAccessResourceFailureException",
        "org.springframework.jdbc.CannotGetJdbcConnectionException",
        "java.sql.SQLTransientConnectionException",
        "java.sql.SQLNonTransientConnectionException",
        "java.net.ConnectException");
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
            if (unavailable(failure)) {
                // The subscriber or its database is not there: wait for it without spending an attempt (Kafka design
                // §14 K11), as the relay waits for the broker (D21), instead of parking and passing the record.
                Duration wait = policy.backoffAfter(1).compareTo(MIN_WAIT) < 0 ? MIN_WAIT : policy.backoffAfter(1);
                metrics.recordRetry(message.messageType(), OWNER_UNAVAILABLE);
                log.info("event=event.subscriber_waiting subscription={} type={} event_id={} code={} attempt={}",
                    subscription.name(), message.messageType(), message.eventId(), failure.errorCode(), attempt);
                return Outcome.waitFor(OWNER_UNAVAILABLE, wait);
            }
            return failed(message, failure.errorCode(), failure.permanent(), attempt);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            // An Error from a handler fails this delivery, not the consumer's thread (Kafka design §14 K10).
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

    /** Whether the failure says the subscriber or its database is not there, rather than that the handler failed. */
    static boolean unavailable(EventDeliveryException failure) {
        if (NOT_READY.contains(failure.errorCode())) {
            return true;
        }
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof io.runtime.sdk.capability.PersistenceCapabilityException persistence
                    && persistence.errorCode() == io.runtime.sdk.capability.PersistenceCapabilityException.Code.UNAVAILABLE) {
                return true;
            }
            for (Class<?> type = cause.getClass(); type != null; type = type.getSuperclass()) {
                if (UNAVAILABLE_TYPES.contains(type.getName())) {
                    return true;
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private Outcome failed(CanonicalOutboxMessage message, String errorCode, boolean permanent, int attempt) {
        if (permanent || attempt >= policy.maxAttempts()) {
            return Outcome.park(errorCode);
        }
        Duration delay = policy.backoffAfter(attempt).plus(jitter.apply(attempt));
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

        static Outcome waitFor(String errorCode, Duration delay) {
            return new Outcome(Kind.WAIT, errorCode, delay);
        }
    }

    /** What happens to a record. */
    public enum Kind {
        /** Done: move past it. */
        DELIVERED,
        /** Deliver it again after the delay, before any later record of its partition. */
        RETRY,
        /** Keep it in the subscription's parking place, then move past it. */
        PARK,
        /** The subscriber cannot take the record now: try the same attempt again after the delay. */
        WAIT
    }
}
