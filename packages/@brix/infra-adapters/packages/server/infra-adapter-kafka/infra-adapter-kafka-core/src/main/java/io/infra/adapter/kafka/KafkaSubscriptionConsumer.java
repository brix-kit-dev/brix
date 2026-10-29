/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.event.SubscriptionDelivery;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Consumes one subscription from its event type's topic (Kafka design §4.2, §11.2).
 * <p>One consumer in the subscription's own group, on one thread. A record's offset is committed only after
 * the subscriber committed the record, so a crash in between delivers it again and the inbox skips it (root
 * BRX-RS-MESSAGE-025). A failed record stays where it is: the consumer seeks its partition back to it and
 * pauses that partition until the retry is due, while the other partitions go on and the consumer keeps
 * polling, so it stays in its group (root BRX-RS-MESSAGE-027). A record the subscription parks is written to
 * its parking topic before its offset is committed. A group without a committed offset starts from the
 * earliest record, so an event sent before the group first joined is not skipped.</p>
 */
final class KafkaSubscriptionConsumer implements EventSubscriptionTransport.Consumption {

    /** How long to wait before parking a record again when the parking topic did not take it. */
    static final Duration PARK_RETRY = Duration.ofSeconds(5);
    /** How long to wait before consuming again after the consumer failed. */
    static final Duration RESTART_DELAY = Duration.ofSeconds(5);
    private static final Duration COMMIT_TIMEOUT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(KafkaSubscriptionConsumer.class);

    private final ConsumedSubscription subscription;
    private final SubscriptionDelivery delivery;
    private final Supplier<Consumer<String, String>> consumers;
    private final KafkaParkingLot parking;
    private final CanonicalEnvelopeCodec envelopes;
    private final String topic;
    private final Duration pollTimeout;
    private final Clock clock;
    private final Map<TopicPartition, Failing> failing = new HashMap<>();
    private final Map<TopicPartition, OffsetAndMetadata> done = new HashMap<>();
    private final Thread thread;
    private volatile boolean stopping;
    private volatile boolean running;

    KafkaSubscriptionConsumer(
            ConsumedSubscription subscription,
            SubscriptionDelivery delivery,
            Supplier<Consumer<String, String>> consumers,
            KafkaParkingLot parking,
            CanonicalEnvelopeCodec envelopes,
            String topic,
            Duration pollTimeout,
            Clock clock) {
        this.subscription = Objects.requireNonNull(subscription, "subscription must not be null");
        this.delivery = Objects.requireNonNull(delivery, "delivery must not be null");
        this.consumers = Objects.requireNonNull(consumers, "consumers must not be null");
        this.parking = Objects.requireNonNull(parking, "parking must not be null");
        this.envelopes = Objects.requireNonNull(envelopes, "envelopes must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
        this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.thread = new Thread(this::run, "brix-event-" + subscription.name());
        this.thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    @Override
    public boolean running() {
        return running;
    }

    @Override
    public void stop(Duration timeout) {
        stopping = true;
        try {
            thread.join(timeout.plus(pollTimeout).toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            log.warn("event=event.consumer_stop_timeout subscription={}", subscription.name());
        }
    }

    private void run() {
        while (!stopping) {
            try (Consumer<String, String> consumer = consumers.get()) {
                consumer.subscribe(List.of(topic), new Rebalance(consumer));
                running = true;
                log.info("event=event.consumer_started subscription={} topic={}", subscription.name(), topic);
                while (!stopping) {
                    resumeDue(consumer);
                    ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
                    for (TopicPartition partition : records.partitions()) {
                        for (ConsumerRecord<String, String> record : records.records(partition)) {
                            if (stopping || !handle(consumer, partition, record)) {
                                break;
                            }
                        }
                    }
                    commit(consumer);
                    report(consumer);
                }
                commit(consumer);
            } catch (RuntimeException failure) {
                log.error("event=event.consumer_failed subscription={}", subscription.name(), failure);
                failing.clear();
                done.clear();
                pause(RESTART_DELAY);
            } finally {
                running = false;
            }
        }
        log.info("event=event.consumer_stopped subscription={}", subscription.name());
    }

    /** Handles a record; false when its partition is held back on it. */
    private boolean handle(Consumer<String, String> consumer, TopicPartition partition, ConsumerRecord<String, String> record) {
        Failing previous = failing.get(partition);
        boolean again = previous != null && previous.offset() == record.offset();
        int attempt = again ? previous.attempt() : 1;
        CanonicalOutboxMessage message;
        try {
            message = envelopes.read(record.value());
        } catch (IllegalArgumentException notAnEnvelope) {
            return park(consumer, partition, record, null, SubscriptionDelivery.ENVELOPE_INVALID, attempt, again ? previous : null);
        }
        SubscriptionDelivery.Outcome outcome = delivery.receive(message, attempt);
        return switch (outcome.kind()) {
            case DELIVERED -> {
                done(partition, record);
                yield true;
            }
            case PARK -> park(consumer, partition, record, message, outcome.errorCode(), attempt, again ? previous : null);
            case RETRY -> {
                holdBack(consumer, partition, record, attempt + 1, again ? previous : null, outcome.delay());
                yield false;
            }
        };
    }

    private boolean park(Consumer<String, String> consumer, TopicPartition partition, ConsumerRecord<String, String> record,
            CanonicalOutboxMessage message, String errorCode, int attempt, Failing previous) {
        String eventId;
        try {
            eventId = parking.park(subscription, record, message, errorCode, attempt);
        } catch (RuntimeException failure) {
            log.warn("event=event.park_failed subscription={} partition={} offset={} code={}", subscription.name(),
                partition, record.offset(), errorCode, failure);
            // The same attempt comes back, so the next pass parks the record again instead of retrying it.
            holdBack(consumer, partition, record, attempt, previous, PARK_RETRY);
            return false;
        }
        delivery.parked(message == null ? null : message.messageType(), eventId, errorCode, attempt);
        done(partition, record);
        return true;
    }

    private void holdBack(Consumer<String, String> consumer, TopicPartition partition, ConsumerRecord<String, String> record,
            int nextAttempt, Failing previous, Duration delay) {
        Instant now = clock.instant();
        Instant since = previous == null ? now : previous.since();
        failing.put(partition, new Failing(record.offset(), nextAttempt, since, now.plus(delay)));
        consumer.seek(partition, record.offset());
        consumer.pause(List.of(partition));
    }

    private void done(TopicPartition partition, ConsumerRecord<String, String> record) {
        failing.remove(partition);
        done.put(partition, new OffsetAndMetadata(record.offset() + 1));
    }

    private void resumeDue(Consumer<String, String> consumer) {
        Instant now = clock.instant();
        List<TopicPartition> due = failing.entrySet().stream()
            .filter(entry -> !now.isBefore(entry.getValue().retryAt()))
            .map(Map.Entry::getKey)
            .filter(consumer.paused()::contains)
            .toList();
        if (!due.isEmpty()) {
            consumer.resume(due);
        }
    }

    private void commit(Consumer<String, String> consumer) {
        if (done.isEmpty()) {
            return;
        }
        try {
            consumer.commitSync(Map.copyOf(done), COMMIT_TIMEOUT);
            done.clear();
        } catch (RuntimeException failure) {
            // The records come again, and the inbox skips them.
            log.warn("event=event.commit_failed subscription={} partitions={}", subscription.name(), done.keySet(), failure);
            done.keySet().retainAll(consumer.assignment());
        }
    }

    private void report(Consumer<String, String> consumer) {
        long lag = 0;
        for (TopicPartition partition : consumer.assignment()) {
            OptionalLong behind = consumer.currentLag(partition);
            if (behind.isPresent()) {
                lag += behind.getAsLong();
            }
        }
        delivery.metrics().recordLag(lag);
        Instant now = clock.instant();
        delivery.metrics().recordStalled(failing.values().stream()
            .map(failure -> Duration.between(failure.since(), now))
            .max(Comparator.naturalOrder())
            .orElse(Duration.ZERO));
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            stopping = true;
        }
    }

    /** Keeps the position reached before a partition moves to another consumer. */
    private final class Rebalance implements ConsumerRebalanceListener {

        private final Consumer<String, String> consumer;

        Rebalance(Consumer<String, String> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            commit(consumer);
            partitions.forEach(partition -> {
                failing.remove(partition);
                done.remove(partition);
            });
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        }

        @Override
        public void onPartitionsLost(Collection<TopicPartition> partitions) {
            partitions.forEach(partition -> {
                failing.remove(partition);
                done.remove(partition);
            });
        }
    }

    /**
     * A partition held back on a failing record.
     *
     * @param offset the record's offset
     * @param attempt the attempt the record gets next
     * @param since when the record first failed
     * @param retryAt when the partition resumes
     */
    private record Failing(long offset, int attempt, Instant since, Instant retryAt) {
    }
}
