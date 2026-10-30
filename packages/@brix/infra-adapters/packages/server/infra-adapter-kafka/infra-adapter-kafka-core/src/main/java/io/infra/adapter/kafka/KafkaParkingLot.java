/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.ParkedEvent;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The parking topics of the subscriptions (Kafka design D20, §11.2; root BRX-RS-MESSAGE-028).
 * <p>Parking a record writes the record as it was received to the subscription's parking topic, keyed by its
 * event id, with headers that say why, after how many attempts, when, and where the record was. Releasing
 * writes a tombstone for the key. The parking topics are compacted, so a parked record stays until it is
 * released, however long that takes. A reader started with the subscriptions reads every parking topic from
 * the beginning and keeps what is parked now; each host instance reads the topics, so all see the same.</p>
 */
final class KafkaParkingLot implements AutoCloseable {

    static final String CODE = "parked.code";
    static final String ATTEMPTS = "parked.attempts";
    static final String PARKED_AT = "parked.at";
    static final String SOURCE = "parked.source";
    static final String EVENT_TYPE = "eventType";

    private static final Logger log = LoggerFactory.getLogger(KafkaParkingLot.class);

    private final KafkaTemplate<String, String> producer;
    private final KafkaEventTopics topics;
    private final Supplier<Consumer<String, String>> readers;
    private final CanonicalEnvelopeCodec envelopes;
    private final KafkaEventSettings settings;
    private final Clock clock;
    private final Map<String, Map<String, Parked>> parked = new ConcurrentHashMap<>();
    private final Map<String, ConsumedSubscription> subscriptionsByTopic = new ConcurrentHashMap<>();
    private Thread reader;
    private volatile boolean stopping;

    KafkaParkingLot(
            KafkaTemplate<String, String> producer,
            KafkaEventTopics topics,
            Supplier<Consumer<String, String>> readers,
            CanonicalEnvelopeCodec envelopes,
            KafkaEventSettings settings,
            Clock clock) {
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.topics = Objects.requireNonNull(topics, "topics must not be null");
        this.readers = Objects.requireNonNull(readers, "readers must not be null");
        this.envelopes = Objects.requireNonNull(envelopes, "envelopes must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Starts reading the parking topics of these subscriptions from the beginning; later calls do nothing.
     *
     * @param subscriptions the subscriptions, whose parking topics exist
     */
    synchronized void watch(List<ConsumedSubscription> subscriptions) {
        if (reader != null || subscriptions.isEmpty()) {
            return;
        }
        subscriptions.forEach(subscription -> subscriptionsByTopic.put(topics.parkingTopic(subscription), subscription));
        reader = new Thread(this::read, "brix-event-parking");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Parks a record of a subscription.
     *
     * @param subscription the subscription
     * @param record the record as received
     * @param message the event, or {@code null} when the record is not an event envelope
     * @param errorCode why it is parked
     * @param attempts deliveries tried
     * @return the id it is parked under: the event id, or where the record was
     * @throws IllegalStateException when the broker did not take the parking record
     */
    String park(ConsumedSubscription subscription, ConsumerRecord<String, String> record, CanonicalOutboxMessage message,
            String errorCode, int attempts) {
        String source = record.topic() + "-" + record.partition() + "@" + record.offset();
        String eventId = message == null ? source : message.eventId();
        String eventType = message == null ? null : message.messageType();
        OffsetDateTime parkedAt = OffsetDateTime.now(clock);
        RecordHeaders headers = new RecordHeaders();
        add(headers, CODE, errorCode);
        add(headers, ATTEMPTS, String.valueOf(attempts));
        add(headers, PARKED_AT, parkedAt.toString());
        add(headers, SOURCE, source);
        add(headers, EVENT_TYPE, eventType);
        // A record without a value is parked with an empty one: a null value is the parking topic's tombstone, which
        // every host would read as a release.
        String value = record.value() == null ? "" : record.value();
        send(new ProducerRecord<>(topics.parkingTopic(subscription), null, eventId, value, headers));
        remember(subscription, new Parked(
            new ParkedEvent(subscription.name(), eventId, eventType, errorCode, attempts, parkedAt, source), value));
        return eventId;
    }

    /**
     * Releases a parked event: writes its tombstone.
     *
     * @param subscription the subscription
     * @param eventId the id it is parked under
     * @throws IllegalStateException when the broker did not take the tombstone
     */
    void release(ConsumedSubscription subscription, String eventId) {
        send(new ProducerRecord<>(topics.parkingTopic(subscription), eventId, null));
        Map<String, Parked> events = parked.get(subscription.name());
        if (events != null) {
            events.remove(eventId);
        }
    }

    Map<String, List<ParkedEvent>> list(int limit) {
        Map<String, List<ParkedEvent>> listed = new TreeMap<>();
        parked.forEach((subscription, events) -> {
            List<ParkedEvent> earliest = events.values().stream()
                .map(Parked::event)
                .sorted(Comparator.comparing(ParkedEvent::parkedAt))
                .limit(limit)
                .toList();
            if (!earliest.isEmpty()) {
                listed.put(subscription, earliest);
            }
        });
        return listed;
    }

    Optional<ParkedEvent> find(ConsumedSubscription subscription, String eventId) {
        return parked(subscription, eventId).map(Parked::event);
    }

    Optional<CanonicalOutboxMessage> message(ConsumedSubscription subscription, String eventId) {
        return parked(subscription, eventId).flatMap(event -> {
            try {
                return Optional.of(envelopes.read(event.value()));
            } catch (IllegalArgumentException notAnEnvelope) {
                return Optional.empty();
            }
        });
    }

    @Override
    public void close() {
        Thread reading;
        synchronized (this) {
            stopping = true;
            reading = reader;
        }
        if (reading != null) {
            try {
                reading.join(settings.pollTimeout().multipliedBy(4).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Optional<Parked> parked(ConsumedSubscription subscription, String eventId) {
        Map<String, Parked> events = parked.get(subscription.name());
        return events == null ? Optional.empty() : Optional.ofNullable(events.get(eventId));
    }

    private void remember(ConsumedSubscription subscription, Parked event) {
        parked.computeIfAbsent(subscription.name(), name -> new ConcurrentHashMap<>()).put(event.event().eventId(), event);
    }

    private void read() {
        while (!stopping) {
            try (Consumer<String, String> consumer = readers.get()) {
                List<TopicPartition> partitions = new ArrayList<>();
                boolean known = true;
                for (String topic : subscriptionsByTopic.keySet()) {
                    List<PartitionInfo> infos = consumer.partitionsFor(topic, settings.adminTimeout());
                    if (infos == null || infos.isEmpty()) {
                        known = false;
                    } else {
                        infos.forEach(info -> partitions.add(new TopicPartition(info.topic(), info.partition())));
                    }
                }
                if (!known) {
                    // A topic created a moment ago may not be in the metadata yet.
                    log.debug("event=event.parking_topics_pending topics={}", subscriptionsByTopic.keySet());
                    pause();
                    continue;
                }
                consumer.assign(partitions);
                consumer.seekToBeginning(partitions);
                while (!stopping) {
                    for (ConsumerRecord<String, String> record : consumer.poll(settings.pollTimeout())) {
                        apply(record);
                    }
                }
            } catch (RuntimeException | Error failure) {
                // The reader keeps every host's view of the parked events; whatever stops it, it reads again.
                if (!stopping) {
                    log.warn("event=event.parking_read_failed", failure);
                    pause();
                }
            }
        }
    }

    private void apply(ConsumerRecord<String, String> record) {
        ConsumedSubscription subscription = subscriptionsByTopic.get(record.topic());
        if (subscription == null || record.key() == null) {
            return;
        }
        if (record.value() == null) {
            Map<String, Parked> events = parked.get(subscription.name());
            if (events != null) {
                events.remove(record.key());
            }
            return;
        }
        remember(subscription, new Parked(new ParkedEvent(subscription.name(), record.key(), header(record, EVENT_TYPE),
            header(record, CODE), attempts(header(record, ATTEMPTS)), parkedAt(header(record, PARKED_AT), record.timestamp()),
            header(record, SOURCE)), record.value()));
    }

    private void send(ProducerRecord<String, String> record) {
        try {
            producer.send(record).get(settings.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing to " + record.topic(), interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Kafka did not take the record for " + record.topic(),
                failure.getCause() == null ? failure : failure.getCause());
        }
    }

    private void pause() {
        try {
            Thread.sleep(settings.pollTimeout().multipliedBy(10).toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            stopping = true;
        }
    }

    private static void add(RecordHeaders headers, String key, String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static int attempts(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException unreadable) {
            return 0;
        }
    }

    private static OffsetDateTime parkedAt(String value, long timestamp) {
        try {
            return value == null
                ? OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
                : OffsetDateTime.parse(value);
        } catch (DateTimeParseException unreadable) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
        }
    }

    private record Parked(ParkedEvent event, String value) {
    }
}
