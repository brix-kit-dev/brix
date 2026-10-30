/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.mock.MockProducerFactory;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.ParkedEvent;
import io.runtime.orchestrator.event.SubscriptionDelivery;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The subscriptions' parking topics (Kafka design D20; root BRX-RS-MESSAGE-028): a record is parked as it was received,
 * keyed by its event id, with why, after how many attempts, when and from where; every host reads the parking topics
 * back and lists the same, and releasing writes a tombstone.
 */
class KafkaParkingLotTest {

    private static final ConsumedSubscription INVOICES =
        new ConsumedSubscription("billing", "invoice-on-order.v1", "OrderPlaced");
    private static final ConsumedSubscription RECEIPTS =
        new ConsumedSubscription("mailing", "receipt-on-order.v1", "OrderPlaced");
    private static final String INVOICES_PARKED = "brix.parked.billing.invoice-on-order.v1";
    private static final String RECEIPTS_PARKED = "brix.parked.mailing.receipt-on-order.v1";
    private static final String EVENTS = "brix.event.OrderPlaced";
    private static final KafkaEventSettings SETTINGS = new KafkaEventSettings(3, (short) 1, (short) 1,
        Duration.ofDays(7), 10, Duration.ofMillis(10), Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofMillis(100));

    private final MutableClock clock = new MutableClock();
    private final CanonicalEnvelopeCodec envelopes = new CanonicalEnvelopeCodec(JsonMapper.builder()
        .configureForJackson2()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build());
    private final ParkingTopics kafka = new ParkingTopics();
    private final AtomicLong offsets = new AtomicLong(40);
    private final List<KafkaParkingLot> lots = new ArrayList<>();

    @AfterEach
    void closeLots() {
        lots.forEach(KafkaParkingLot::close);
    }

    @Test
    void parkingWritesTheRecordAsReceivedKeyedByItsEventIdWithWhyWhenAndWhereItWas() {
        KafkaParkingLot lot = lot();
        CanonicalOutboxMessage message = message("evt-7");
        ConsumerRecord<String, String> received = new ConsumerRecord<>(EVENTS, 2, 41, "order-7", envelopes.write(message));

        String parkedAs = lot.park(INVOICES, received, message, EventDelivery.HANDLER_FAILED, 6);

        assertThat(parkedAs).isEqualTo("evt-7");
        assertThat(kafka.records(INVOICES_PARKED)).singleElement().satisfies(parked -> {
            assertThat(parked.key()).isEqualTo("evt-7");
            assertThat(parked.value()).isEqualTo(received.value());
            assertThat(header(parked, KafkaParkingLot.CODE)).isEqualTo(EventDelivery.HANDLER_FAILED);
            assertThat(header(parked, KafkaParkingLot.ATTEMPTS)).isEqualTo("6");
            assertThat(OffsetDateTime.parse(header(parked, KafkaParkingLot.PARKED_AT))).isEqualTo(now());
            assertThat(header(parked, KafkaParkingLot.SOURCE)).isEqualTo(EVENTS + "-2@41");
            assertThat(header(parked, KafkaParkingLot.EVENT_TYPE)).isEqualTo("OrderPlaced");
        });
        ParkedEvent expected = new ParkedEvent(INVOICES.name(), "evt-7", "OrderPlaced", EventDelivery.HANDLER_FAILED, 6,
            now(), EVENTS + "-2@41");
        assertThat(lot.find(INVOICES, "evt-7")).contains(expected);
        assertThat(lot.list(10)).isEqualTo(Map.of(INVOICES.name(), List.of(expected)));
        assertThat(lot.message(INVOICES, "evt-7")).as("the event as it was received, to deliver it again")
            .contains(message);
        assertThat(lot.find(RECEIPTS, "evt-7")).as("each subscription has its own parking topic").isEmpty();
    }

    @Test
    void aRecordThatIsNotAnEventEnvelopeIsParkedUnderWhereTheBrokerHeldIt() {
        KafkaParkingLot lot = lot();
        ConsumerRecord<String, String> received = new ConsumerRecord<>(EVENTS, 0, 3, "order-3", "not an envelope");

        String parkedAs = lot.park(INVOICES, received, null, SubscriptionDelivery.ENVELOPE_INVALID, 1);

        assertThat(parkedAs).isEqualTo(EVENTS + "-0@3");
        assertThat(kafka.records(INVOICES_PARKED)).singleElement().satisfies(parked -> {
            assertThat(parked.key()).isEqualTo(EVENTS + "-0@3");
            assertThat(parked.value()).isEqualTo("not an envelope");
            assertThat(header(parked, KafkaParkingLot.CODE)).isEqualTo(SubscriptionDelivery.ENVELOPE_INVALID);
            assertThat(header(parked, KafkaParkingLot.EVENT_TYPE)).isNull();
        });
        assertThat(lot.find(INVOICES, EVENTS + "-0@3")).contains(new ParkedEvent(INVOICES.name(), EVENTS + "-0@3", null,
            SubscriptionDelivery.ENVELOPE_INVALID, 1, now(), EVENTS + "-0@3"));
        assertThat(lot.list(10).get(INVOICES.name())).extracting(ParkedEvent::eventId).containsExactly(EVENTS + "-0@3");
        assertThat(lot.message(INVOICES, EVENTS + "-0@3")).as("there is no event to deliver again").isEmpty();
    }

    @Test
    void aRecordWithoutAValueIsParkedWithAnEmptyOneSoNoHostTakesItForARelease() {
        // A null value on the compacted parking topic is a tombstone: written as is, every other host, and this one
        // after a restart, would read the record as released while its offset is already committed.
        KafkaParkingLot parkedHere = lot();
        ConsumerRecord<String, String> received = received(null);
        String parkedAs = parkedHere.park(INVOICES, received, null, SubscriptionDelivery.ENVELOPE_INVALID, 1);
        KafkaParkingLot anotherHost = lot();

        anotherHost.watch(List.of(INVOICES));

        assertThat(kafka.records(INVOICES_PARKED)).singleElement()
            .satisfies(parked -> assertThat(parked.value()).isEmpty());
        await("the parked record read back", () -> anotherHost.find(INVOICES, parkedAs).isPresent());
        assertThat(anotherHost.list(10)).isEqualTo(parkedHere.list(10));
        assertThat(anotherHost.message(INVOICES, parkedAs)).as("there is no event to deliver again").isEmpty();
    }

    @Test
    void everyHostReadsTheParkingTopicsBackAndListsTheSameParkedEvents() {
        KafkaParkingLot parkedHere = lot();
        CanonicalOutboxMessage first = message("evt-1");
        parkedHere.park(INVOICES, received(envelopes.write(first)), first, EventDelivery.HANDLER_FAILED, 6);
        clock.advance(Duration.ofMinutes(1));
        ConsumerRecord<String, String> notAnEnvelope = received("not an envelope");
        String notAnEnvelopeId = parkedHere.park(INVOICES, notAnEnvelope, null, SubscriptionDelivery.ENVELOPE_INVALID, 1);
        clock.advance(Duration.ofMinutes(1));
        CanonicalOutboxMessage second = message("evt-2");
        parkedHere.park(RECEIPTS, received(envelopes.write(second)), second, EventDelivery.SCHEMA_UNSUPPORTED, 1);
        KafkaParkingLot anotherHost = lot();

        anotherHost.watch(List.of(INVOICES, RECEIPTS));

        await("the parked events read back", () -> anotherHost.list(10).equals(parkedHere.list(10)));
        assertThat(anotherHost.list(10).get(INVOICES.name())).extracting(ParkedEvent::eventId)
            .containsExactly("evt-1", notAnEnvelopeId);
        assertThat(anotherHost.find(INVOICES, notAnEnvelopeId)).hasValueSatisfying(parked -> {
            assertThat(parked.eventType()).isNull();
            assertThat(parked.errorCode()).isEqualTo(SubscriptionDelivery.ENVELOPE_INVALID);
            assertThat(parked.source()).isEqualTo(notAnEnvelopeId);
        });
        assertThat(anotherHost.message(INVOICES, "evt-1")).contains(first);
        assertThat(anotherHost.message(INVOICES, notAnEnvelopeId)).isEmpty();
        assertThat(anotherHost.message(RECEIPTS, "evt-2")).contains(second);
    }

    @Test
    void releasingWritesATombstoneSoTheEventIsListedNeitherHereNorOnAnotherHost() {
        KafkaParkingLot lot = lot();
        park(lot, INVOICES, "evt-1");
        clock.advance(Duration.ofMinutes(1));
        park(lot, INVOICES, "evt-2");
        KafkaParkingLot anotherHost = lot();
        anotherHost.watch(List.of(INVOICES));
        await("both parked events read back", () -> anotherHost.find(INVOICES, "evt-2").isPresent());

        lot.release(INVOICES, "evt-1");

        assertThat(kafka.records(INVOICES_PARKED)).last().satisfies(tombstone -> {
            assertThat(tombstone.key()).isEqualTo("evt-1");
            assertThat(tombstone.value()).isNull();
        });
        assertThat(lot.find(INVOICES, "evt-1")).isEmpty();
        assertThat(lot.list(10).get(INVOICES.name())).extracting(ParkedEvent::eventId).containsExactly("evt-2");
        await("the tombstone read on the other host", () -> anotherHost.find(INVOICES, "evt-1").isEmpty());
        assertThat(anotherHost.list(10)).isEqualTo(lot.list(10));
    }

    @Test
    void theEarliestParkedEventsAreListedFirstUpToTheLimitAndASubscriptionWithNoneIsLeftOut() {
        KafkaParkingLot lot = lot();
        Instant start = clock.instant();
        clock.set(start.plus(Duration.ofMinutes(2)));
        park(lot, INVOICES, "evt-3");
        clock.set(start);
        park(lot, INVOICES, "evt-1");
        clock.set(start.plus(Duration.ofMinutes(1)));
        park(lot, INVOICES, "evt-2");
        park(lot, RECEIPTS, "evt-4");
        lot.release(RECEIPTS, "evt-4");

        assertThat(lot.list(2)).containsOnlyKeys(INVOICES.name());
        assertThat(lot.list(2).get(INVOICES.name())).extracting(ParkedEvent::eventId).containsExactly("evt-1", "evt-2");
        assertThat(lot.list(10).get(INVOICES.name())).extracting(ParkedEvent::eventId)
            .containsExactly("evt-1", "evt-2", "evt-3");
    }

    @Test
    void aParkingRecordTheBrokerDoesNotTakeFailsAndIsNotListed() {
        KafkaParkingLot lot = lot();
        CanonicalOutboxMessage message = message("evt-1");
        kafka.refusing = true;

        assertThatThrownBy(() -> lot.park(INVOICES, received(envelopes.write(message)), message,
            EventDelivery.HANDLER_FAILED, 6))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(INVOICES_PARKED);
        assertThat(lot.find(INVOICES, "evt-1")).isEmpty();
        assertThat(lot.list(10)).isEmpty();
    }

    @Test
    void aReleaseTheBrokerDoesNotTakeFailsAndTheEventStaysParked() {
        KafkaParkingLot lot = lot();
        park(lot, INVOICES, "evt-1");
        kafka.refusing = true;

        assertThatThrownBy(() -> lot.release(INVOICES, "evt-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(INVOICES_PARKED);
        assertThat(lot.find(INVOICES, "evt-1")).isPresent();
        assertThat(kafka.records(INVOICES_PARKED)).extracting(ConsumerRecord::value).doesNotContainNull();
    }

    @Test
    void aParkingRecordWithMissingOrUnreadableHeadersIsStillListedByItsKey() {
        clock.set(Instant.parse("2026-10-30T08:00:00Z"));
        kafka.append(INVOICES_PARKED, null, "a record without a key is no parked event");
        kafka.append(INVOICES_PARKED, "evt-1", "not an envelope");
        kafka.append(INVOICES_PARKED, "evt-2", envelopes.write(message("evt-2")),
            newHeader(KafkaParkingLot.ATTEMPTS, "six"), newHeader(KafkaParkingLot.PARKED_AT, "yesterday"));
        clock.set(Instant.parse("2026-10-30T09:00:00Z"));
        KafkaParkingLot lot = lot();

        lot.watch(List.of(INVOICES));

        await("both parked events read back", () -> lot.find(INVOICES, "evt-2").isPresent());
        OffsetDateTime appended = OffsetDateTime.parse("2026-10-30T08:00:00Z");
        assertThat(lot.list(10).get(INVOICES.name())).containsExactlyInAnyOrder(
            new ParkedEvent(INVOICES.name(), "evt-1", null, null, 0, appended, null),
            new ParkedEvent(INVOICES.name(), "evt-2", null, null, 0, appended, null));
        assertThat(lot.message(INVOICES, "evt-1")).isEmpty();
        assertThat(lot.message(INVOICES, "evt-2")).contains(message("evt-2"));
    }

    @Test
    void theReaderKeepsTryingUntilItHasReadTheParkingTopicsAndStopsWithTheLot() {
        park(lot(), INVOICES, "evt-1");
        Reader unknownTopic = kafka.reader(Set.of());
        Reader disconnected = kafka.reader(Set.of(INVOICES_PARKED));
        disconnected.failNextPoll();
        Reader connected = kafka.reader(Set.of(INVOICES_PARKED));
        Queue<Reader> readers = new ConcurrentLinkedQueue<>(List.of(unknownTopic, disconnected, connected));
        KafkaParkingLot anotherHost = lot(readers::remove);

        anotherHost.watch(List.of(INVOICES));

        await("the parked event read back", () -> anotherHost.find(INVOICES, "evt-1").isPresent());
        assertThat(readers).isEmpty();
        assertThat(unknownTopic.closed()).isTrue();
        assertThat(disconnected.closed()).isTrue();
        anotherHost.close();
        await("the reader closed", connected::closed);
    }

    private KafkaParkingLot lot() {
        return lot(kafka::reader);
    }

    private KafkaParkingLot lot(Supplier<Consumer<String, String>> readers) {
        KafkaParkingLot lot = new KafkaParkingLot(kafka.template(), new KafkaEventTopics(""), readers, envelopes, SETTINGS,
            clock);
        lots.add(lot);
        return lot;
    }

    private void park(KafkaParkingLot lot, ConsumedSubscription subscription, String eventId) {
        CanonicalOutboxMessage message = message(eventId);
        lot.park(subscription, received(envelopes.write(message)), message, EventDelivery.HANDLER_FAILED, 6);
    }

    /** A record as the subscription's consumer received it from the event topic. */
    private ConsumerRecord<String, String> received(String value) {
        return new ConsumerRecord<>(EVENTS, 0, offsets.getAndIncrement(), "order-1", value);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static CanonicalOutboxMessage message(String eventId) {
        return new CanonicalOutboxMessage("msg-" + eventId, eventId, "EVENT", "OrderPlaced", "1.0.0", "STANDARD", "orders",
            "PLATFORM", null, "order-1", OffsetDateTime.parse("2026-10-30T08:59:00Z"), "corr-" + eventId, null, null, null,
            "{\"orderId\":1}", 1);
    }

    private static String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Header newHeader(String key, String value) {
        return new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting for " + what);
            }
            sleep(Duration.ofMillis(5));
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The parking topics as the broker holds them, one partition each: every record written to them, in order, stamped
     * with the broker's time.
     */
    private final class ParkingTopics {

        private final Map<String, List<ConsumerRecord<String, String>>> topics = new ConcurrentHashMap<>();
        private volatile boolean refusing;

        KafkaTemplate<String, String> template() {
            return new KafkaTemplate<>(new MockProducerFactory<String, String>(this::producer));
        }

        synchronized void append(String topic, String key, String value, Header... headers) {
            List<ConsumerRecord<String, String>> records = topics.computeIfAbsent(topic, name -> new CopyOnWriteArrayList<>());
            records.add(new ConsumerRecord<>(topic, 0, records.size(), clock.millis(), TimestampType.LOG_APPEND_TIME, -1, -1,
                key, value, new RecordHeaders(headers), Optional.empty()));
        }

        List<ConsumerRecord<String, String>> records(String topic) {
            return new ArrayList<>(topics.getOrDefault(topic, List.of()));
        }

        /** A host's reader, which finds both parking topics in the metadata. */
        Consumer<String, String> reader() {
            return reader(Set.of(INVOICES_PARKED, RECEIPTS_PARKED));
        }

        Reader reader(Set<String> known) {
            return new Reader(known);
        }

        private MockProducer<String, String> producer() {
            if (refusing) {
                // A write the broker refuses is never acknowledged, so the parking lot gives up after its send timeout.
                return new MockProducer<>(false, null, new StringSerializer(), new StringSerializer());
            }
            return new MockProducer<>(true, null, new StringSerializer(), new StringSerializer()) {
                @Override
                public synchronized Future<RecordMetadata> send(ProducerRecord<String, String> record, Callback callback) {
                    append(record.topic(), record.key(), record.value(), record.headers().toArray());
                    return super.send(record, callback);
                }
            };
        }
    }

    /**
     * A host's reader of the parking topics: it knows the topics the metadata holds, fetches what they hold from its
     * positions, and with nothing new waits out its poll timeout, as a real consumer does.
     */
    private final class Reader extends MockConsumer<String, String> {

        private boolean failNextPoll;

        Reader(Set<String> known) {
            super("earliest");
            for (String topic : known) {
                updatePartitions(topic, List.of(new PartitionInfo(topic, 0, null, new Node[0], new Node[0])));
                updateBeginningOffsets(Map.of(new TopicPartition(topic, 0), 0L));
            }
        }

        synchronized void failNextPoll() {
            failNextPoll = true;
        }

        @Override
        public ConsumerRecords<String, String> poll(Duration timeout) {
            Map<TopicPartition, List<ConsumerRecord<String, String>>> fetched = new HashMap<>();
            synchronized (this) {
                if (failNextPoll) {
                    failNextPoll = false;
                    throw new KafkaException("the connection to the broker was lost");
                }
                for (TopicPartition partition : assignment()) {
                    long position = position(partition);
                    List<ConsumerRecord<String, String>> next = kafka.records(partition.topic()).stream()
                        .filter(record -> record.offset() >= position)
                        .toList();
                    if (!next.isEmpty()) {
                        fetched.put(partition, next);
                        seek(partition, next.get(next.size() - 1).offset() + 1);
                    }
                }
            }
            if (fetched.isEmpty()) {
                sleep(timeout);
            }
            return new ConsumerRecords<>(fetched, Map.of());
        }
    }

    /** A clock that moves only when the test moves it. */
    private static final class MutableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-10-30T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
