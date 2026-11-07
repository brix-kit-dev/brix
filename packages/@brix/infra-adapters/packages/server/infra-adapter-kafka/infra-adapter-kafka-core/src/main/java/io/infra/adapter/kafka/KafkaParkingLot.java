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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
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
 * <p>The topic holds the payloads, and the hosts keep only what they list and where each record is, so a host's heap
 * grows with how many records are parked, not with how large they are (Kafka design D20; standard-link plan D88).
 * Delivering an event again reads its record back from the topic. A record's offset holds while it is the latest of its
 * key, which is what compaction keeps.</p>
 * <p>Until a host has read the topics up to where they ended when it started reading, it does not know what is parked,
 * and it says so instead of answering that nothing is (root BRX-RS-RESILIENCE-005; standard-link plan D89). A reader that
 * stops reads the topics again from the beginning, with the same effect.</p>
 */
final class KafkaParkingLot implements AutoCloseable {

    static final String CODE = "parked.code";
    static final String ATTEMPTS = "parked.attempts";
    static final String PARKED_AT = "parked.at";
    static final String SOURCE = "parked.source";
    static final String EVENT_TYPE = "eventType";

    /** Records read back at once: each takes a consumer of its own. */
    static final int READS_AT_ONCE = 4;

    private static final Logger log = LoggerFactory.getLogger(KafkaParkingLot.class);

    private final KafkaTemplate<String, String> producer;
    private final KafkaEventTopics topics;
    private final Supplier<Consumer<String, String>> readers;
    private final CanonicalEnvelopeCodec envelopes;
    private final KafkaEventSettings settings;
    private final Clock clock;
    private final Map<String, Map<String, Parked>> parked = new ConcurrentHashMap<>();
    private final Map<String, ConsumedSubscription> subscriptionsByTopic = new ConcurrentHashMap<>();
    private final Semaphore reads = new Semaphore(READS_AT_ONCE);
    private Thread reader;
    private volatile boolean stopping;
    private volatile boolean behind = true;

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
        if (reader != null) {
            return;
        }
        if (subscriptions.isEmpty()) {
            behind = false;
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
        RecordMetadata written = send(new ProducerRecord<>(topics.parkingTopic(subscription), null, eventId, value, headers));
        String producerPluginId = message == null ? null : message.producerPluginId();
        Long tenantId = message == null ? null : message.tenantId();
        remember(subscription, new Parked(new ParkedEvent(subscription.name(), eventId, eventType, errorCode, attempts,
            parkedAt, source, producerPluginId, tenantId), written.partition(), written.offset()));
        return eventId;
    }

    /**
     * Releases a parked event: writes its tombstone. This host stops listing it at once, but a reader that has not got past
     * the event's record yet lists it again until it reads the tombstone, as any other host does.
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

    /**
     * Lists the parked events.
     *
     * @param limit most events listed per subscription
     * @return the earliest parked events by subscription; a subscription with none is left out
     * @throws IllegalStateException while this host does not know what is parked
     */
    Map<String, List<ParkedEvent>> list(int limit) {
        requireRead();
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

    /**
     * Finds a parked event.
     *
     * @param subscription the subscription
     * @param eventId the id it is parked under
     * @return the event, or empty when none is parked with this id
     * @throws IllegalStateException while this host does not know what is parked
     */
    Optional<ParkedEvent> find(ConsumedSubscription subscription, String eventId) {
        return parked(subscription, eventId).map(Parked::event);
    }

    /**
     * Reads a parked event back from its topic.
     *
     * @param subscription the subscription
     * @param eventId the id it is parked under
     * @return the event, or empty when none is parked with this id or the record is not an event envelope
     * @throws RuntimeException when the broker does not give the record back now; the event stays parked
     */
    Optional<CanonicalOutboxMessage> message(ConsumedSubscription subscription, String eventId) {
        return parked(subscription, eventId).flatMap(event -> envelope(stored(subscription, eventId, event)));
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
        requireRead();
        Map<String, Parked> events = parked.get(subscription.name());
        return events == null ? Optional.empty() : Optional.ofNullable(events.get(eventId));
    }

    private void requireRead() {
        if (behind) {
            throw new IllegalStateException("This host has not read the parking topics yet");
        }
    }

    /** Reads a record back at its offset, within the admin timeout, on a consumer of its own. */
    private String stored(ConsumedSubscription subscription, String eventId, Parked parked) {
        long deadline = System.nanoTime() + settings.adminTimeout().toNanos();
        try {
            if (!reads.tryAcquire(settings.adminTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Too many parked records are being read back");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to read back " + eventId, interrupted);
        }
        try {
            return stored(new TopicPartition(topics.parkingTopic(subscription), parked.partition()), parked.offset(), eventId,
                deadline);
        } finally {
            reads.release();
        }
    }

    private String stored(TopicPartition partition, long offset, String eventId, long deadline) {
        try (Consumer<String, String> consumer = readers.get()) {
            consumer.assign(List.of(partition));
            consumer.seek(partition, offset);
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(settings.pollTimeout()).records(partition)) {
                    if (record.offset() == offset && eventId.equals(record.key())) {
                        return record.value();
                    }
                    // Another record stands there: compaction removed this one, and this host has not read yet what
                    // replaced it. Asking again answers from the record that did.
                    throw new IllegalStateException("The parked record of " + eventId + " has moved");
                }
            }
        }
        throw new IllegalStateException("Kafka did not give back the parked record of " + eventId);
    }

    private Optional<CanonicalOutboxMessage> envelope(String value) {
        try {
            return Optional.of(envelopes.read(value));
        } catch (IllegalArgumentException notAnEnvelope) {
            return Optional.empty();
        }
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
                Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
                // What the topics hold now replaces what was read before: a tombstone may be gone by now.
                parked.clear();
                while (!stopping) {
                    for (ConsumerRecord<String, String> record : consumer.poll(settings.pollTimeout())) {
                        apply(record);
                    }
                    if (behind && reached(consumer, ends)) {
                        behind = false;
                    }
                }
            } catch (RuntimeException | Error failure) {
                // The reader keeps every host's view of the parked events; whatever stops it, it reads again.
                behind = true;
                if (!stopping) {
                    log.warn("event=event.parking_read_failed", failure);
                    pause();
                }
            }
        }
    }

    private static boolean reached(Consumer<String, String> consumer, Map<TopicPartition, Long> ends) {
        return ends.entrySet().stream().allMatch(end -> consumer.position(end.getKey()) >= end.getValue());
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
        // The value is read here and let go: a host keeps who sent the event, so that a status query reads no record.
        Optional<CanonicalOutboxMessage> message = envelope(record.value());
        remember(subscription, new Parked(new ParkedEvent(subscription.name(), record.key(), header(record, EVENT_TYPE),
            header(record, CODE), attempts(header(record, ATTEMPTS)), parkedAt(header(record, PARKED_AT), record.timestamp()),
            header(record, SOURCE), message.map(CanonicalOutboxMessage::producerPluginId).orElse(null),
            message.map(CanonicalOutboxMessage::tenantId).orElse(null)), record.partition(), record.offset()));
    }

    private RecordMetadata send(ProducerRecord<String, String> record) {
        try {
            return producer.send(record).get(settings.sendTimeout().toMillis(), TimeUnit.MILLISECONDS).getRecordMetadata();
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

    /** What a host keeps of a parked record: what it lists and where the record is, never its value. */
    private record Parked(ParkedEvent event, int partition, long offset) {
    }
}
