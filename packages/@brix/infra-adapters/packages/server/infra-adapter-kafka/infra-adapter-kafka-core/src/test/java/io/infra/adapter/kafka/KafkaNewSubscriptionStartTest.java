/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/**
 * Kafka design D32, §14 K15: a new subscription receives what is published from its registration on, not the history
 * the topic retains; a subscription that has a position keeps it.
 */
class KafkaNewSubscriptionStartTest {

    private static EmbeddedKafkaBroker kafka;

    @BeforeAll
    static void startKafka() {
        kafka = new EmbeddedKafkaKraftBroker(1, 3).brokerProperties(Map.of("auto.create.topics.enable", "false"));
        kafka.afterPropertiesSet();
    }

    @AfterAll
    static void stopKafka() {
        kafka.destroy();
    }

    @Test
    void aNewGroupStartsAtItsRegistrationAndAnExistingOneKeepsItsPosition() throws Exception {
        Map<String, Object> client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        KafkaEventTopics topics = new KafkaEventTopics("start-");
        KafkaEventSettings settings = new KafkaEventSettings(1, (short) 1, (short) 1, Duration.ofDays(1), 10,
            Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(10));
        String topic = topics.eventTopic("OrderPlaced");
        try (KafkaEventBroker broker = new KafkaEventBroker(Admin.create(client), topics, settings, Clock.systemUTC());
                KafkaProducer<String, String> producer = producer(client)) {
            broker.ensureEventTopics(Set.of("OrderPlaced"));
            producer.send(new ProducerRecord<>(topic, "order-1", "before")).get();
            Thread.sleep(50);
            Instant registeredAt = Instant.now();
            Thread.sleep(50);
            producer.send(new ProducerRecord<>(topic, "order-2", "after")).get();
            try (Admin admin = Admin.create(client)) {
                admin.alterConsumerGroupOffsets("existing", Map.of(new TopicPartition(topic, 0), new OffsetAndMetadata(0)))
                    .all().get();
            }

            broker.startNewGroupsAt(Map.of("new", topic, "existing", topic), registeredAt);

            assertThat(read("new", topic, client)).containsExactly("after");
            assertThat(read("existing", topic, client)).containsExactly("before", "after");
        }
    }

    private static KafkaProducer<String, String> producer(Map<String, Object> client) {
        Map<String, Object> configs = new HashMap<>(client);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(configs);
    }

    private static List<String> read(String group, String topic, Map<String, Object> client) {
        Map<String, Object> configs = new HashMap<>(client);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(configs)) {
            consumer.subscribe(List.of(topic));
            long until = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < until && values.size() < 2) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    values.add(record.value());
                }
            }
        }
        return values;
    }
}
