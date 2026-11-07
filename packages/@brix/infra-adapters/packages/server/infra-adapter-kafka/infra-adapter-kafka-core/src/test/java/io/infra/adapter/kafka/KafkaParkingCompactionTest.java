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

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * What the parked view counts on, on a real broker with its cleaner running (standard-link plan D88): a host keeps an
 * offset instead of a payload, so the record must stay at that offset while it is the latest of its key, and a host that
 * is behind must not take the record that follows for the one compaction removed.
 */
class KafkaParkingCompactionTest {

    private static final ConsumedSubscription INVOICES =
        new ConsumedSubscription("billing", "invoice-on-order.v1", "OrderPlaced");

    private static EmbeddedKafkaBroker kafka;

    private final CanonicalEnvelopeCodec envelopes = new CanonicalEnvelopeCodec(JsonMapper.builder()
        .configureForJackson2()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build());
    private final KafkaEventSettings settings = new KafkaEventSettings(1, (short) 1, (short) 1, Duration.ofDays(1), 10,
        Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(10));
    private final KafkaEventTopics topics = new KafkaEventTopics("compaction-");
    private final List<KafkaParkingLot> lots = new ArrayList<>();
    private final AtomicBoolean lagging = new AtomicBoolean();
    private DefaultKafkaProducerFactory<String, String> producers;
    private Admin admin;
    private KafkaEventBroker broker;

    @BeforeAll
    static void startKafka() {
        kafka = new EmbeddedKafkaKraftBroker(1, 1).brokerProperties(Map.of(
            "auto.create.topics.enable", "false",
            "log.cleaner.backoff.ms", "100"));
        kafka.afterPropertiesSet();
    }

    @AfterAll
    static void stopKafka() {
        kafka.destroy();
    }

    @AfterEach
    void close() {
        lots.forEach(KafkaParkingLot::close);
        broker.close();
        admin.close();
        producers.destroy();
    }

    @Test
    void aRecordStaysAtItsOffsetAndAHostThatIsBehindDoesNotTakeTheNextRecordForTheOneCompactionRemoved() throws Exception {
        prepareATopicTheCleanerCompactsAtOnce();
        KafkaParkingLot behind = laggingHost();
        KafkaParkingLot upToDate = read(lot());
        lagging.set(true);
        behind.park(INVOICES, received("evt-1", 1), message("evt-1", 1), EventDelivery.HANDLER_FAILED, 6);
        CanonicalOutboxMessage replacing = message("evt-1", 2);
        upToDate.park(INVOICES, received("evt-1", 2), replacing, EventDelivery.HANDLER_FAILED, 6);
        CanonicalOutboxMessage untouched = message("evt-2", 1);
        upToDate.park(INVOICES, received("evt-2", 1), untouched, EventDelivery.HANDLER_FAILED, 6);

        awaitTheCleanerRemovingOffsetZero(upToDate);

        assertThat(upToDate.message(INVOICES, "evt-1")).as("the latest record of its key stays where it was")
            .contains(replacing);
        assertThat(upToDate.message(INVOICES, "evt-2")).contains(untouched);
        assertThatThrownBy(() -> behind.message(INVOICES, "evt-1")).as("the record there now is another one")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("has moved");

        KafkaParkingLot started = read(lot());
        assertThat(started.find(INVOICES, "evt-2")).as("a host started now has read what compaction kept").isPresent();
        assertThat(started.message(INVOICES, "evt-1")).contains(replacing);

        lagging.set(false);
        await("the host that was behind caught up", () -> behind.find(INVOICES, "evt-2").isPresent());
        await("and reads the record that replaced what it knew", () -> behind.message(INVOICES, "evt-1").equals(
            java.util.Optional.of(replacing)));
    }

    private void prepareATopicTheCleanerCompactsAtOnce() throws Exception {
        Map<String, Object> client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        admin = Admin.create(client);
        broker = new KafkaEventBroker(Admin.create(client), topics, settings, Clock.systemUTC());
        broker.ensureParkingTopics(List.of(INVOICES));
        ConfigResource topic = new ConfigResource(ConfigResource.Type.TOPIC, topics.parkingTopic(INVOICES));
        admin.incrementalAlterConfigs(Map.of(topic, List.of(
            new AlterConfigOp(new ConfigEntry(TopicConfig.SEGMENT_MS_CONFIG, "100"), AlterConfigOp.OpType.SET),
            new AlterConfigOp(new ConfigEntry(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.01"),
                AlterConfigOp.OpType.SET)))).all().get();
        Map<String, Object> producer = new HashMap<>(client);
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producers = new DefaultKafkaProducerFactory<>(producer);
    }

    /** The cleaner leaves the active segment alone, so later records have to close the one that holds offset zero. */
    private void awaitTheCleanerRemovingOffsetZero(KafkaParkingLot parking) {
        TopicPartition partition = new TopicPartition(topics.parkingTopic(INVOICES), 0);
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        for (int filler = 0; System.nanoTime() < deadline; filler++) {
            parking.park(INVOICES, received("filler-" + filler, 1), message("filler-" + filler, 1),
                EventDelivery.HANDLER_FAILED, 6);
            if (firstOffset(partition) > 0) {
                return;
            }
            sleep(Duration.ofMillis(150));
        }
        fail("the cleaner did not remove offset 0");
    }

    private long firstOffset(TopicPartition partition) {
        try (Consumer<String, String> consumer = reader()) {
            consumer.assign(List.of(partition));
            consumer.seek(partition, 0);
            for (int polls = 0; polls < 50; polls++) {
                List<ConsumerRecord<String, String>> records = consumer.poll(Duration.ofMillis(100)).records(partition);
                if (!records.isEmpty()) {
                    return records.get(0).offset();
                }
            }
        }
        return -1;
    }

    private KafkaParkingLot lot() {
        return lot(this::reader);
    }

    /** A host whose own reader gets nothing while {@link #lagging} is set; it still reads a record back. */
    private KafkaParkingLot laggingHost() {
        AtomicBoolean first = new AtomicBoolean(true);
        return read(lot(() -> first.getAndSet(false) ? laggingReader() : reader()));
    }

    private KafkaParkingLot lot(java.util.function.Supplier<Consumer<String, String>> readers) {
        KafkaParkingLot lot = new KafkaParkingLot(new KafkaTemplate<>(producers), topics, readers, envelopes, settings,
            Clock.systemUTC());
        lots.add(lot);
        return lot;
    }

    private KafkaParkingLot read(KafkaParkingLot lot) {
        lot.watch(List.of(INVOICES));
        await("the host read the parking topic", () -> lot.list(1) != null);
        return lot;
    }

    private Consumer<String, String> reader() {
        return new KafkaConsumer<>(readerConfigs());
    }

    private Consumer<String, String> laggingReader() {
        return new KafkaConsumer<>(readerConfigs()) {
            @Override
            public ConsumerRecords<String, String> poll(Duration timeout) {
                if (lagging.get()) {
                    sleep(timeout);
                    return ConsumerRecords.empty();
                }
                return super.poll(timeout);
            }
        };
    }

    private Map<String, Object> readerConfigs() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return configs;
    }

    private ConsumerRecord<String, String> received(String eventId, int version) {
        return new ConsumerRecord<>("compaction-brix.event.OrderPlaced", 0, version, eventId,
            envelopes.write(message(eventId, version)));
    }

    private static CanonicalOutboxMessage message(String eventId, int version) {
        return new CanonicalOutboxMessage("msg-" + eventId, eventId, "EVENT", "OrderPlaced", "1.0.0", "STANDARD", "orders",
            "PLATFORM", null, "order-1", OffsetDateTime.parse("2026-10-30T08:59:00Z"), "corr-" + eventId, null, null, null,
            "{\"version\":" + version + "}", 1);
    }

    private static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!holds(condition)) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting for " + what);
            }
            sleep(Duration.ofMillis(20));
        }
    }

    /** A host still reading, or one that has not read yet what replaced a record, does not answer yet. */
    private static boolean holds(BooleanSupplier condition) {
        try {
            return condition.getAsBoolean();
        } catch (IllegalStateException notYet) {
            return false;
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
