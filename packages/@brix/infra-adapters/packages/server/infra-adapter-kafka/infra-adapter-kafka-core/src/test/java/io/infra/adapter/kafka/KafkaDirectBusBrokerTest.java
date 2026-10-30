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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * On a broker that creates no topic by itself, the direct bus creates its event type's topic and sends a BEST_EFFORT
 * canonical envelope there, under the canonical namespace and nowhere else (Kafka design §13 K2, K4).
 */
class KafkaDirectBusBrokerTest {

    private static EmbeddedKafkaBroker kafka;

    /** An event as a plugin publishes it through the direct bus. */
    public static final class MemberSeenEvent extends IntegrationEvent {

        MemberSeenEvent() {
            super("conformance-publisher", "910001");
        }

        @Override
        public String getRoutingKey() {
            return "member:7";
        }

        @Override
        public Object payload() {
            return Map.of("memberId", 7);
        }
    }

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
    void aDirectPublishLandsOnItsEventTypesTopicAsABestEffortEnvelope() throws Exception {
        Map<String, Object> client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        Map<String, Object> producer = new HashMap<>(client);
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        DefaultKafkaProducerFactory<String, String> producers = new DefaultKafkaProducerFactory<>(producer);
        ObjectMapper json = JsonMapper.builder().configureForJackson2()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        KafkaEventTopics topics = new KafkaEventTopics("direct-");
        KafkaEventSettings settings = new KafkaEventSettings(3, (short) 1, (short) 1, Duration.ofDays(1), 10,
            Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(10));
        try (Admin admin = Admin.create(client);
                KafkaEventBroker broker = new KafkaEventBroker(Admin.create(client), topics, settings, Clock.systemUTC())) {
            KafkaEventBusCapability bus = new KafkaEventBusCapability(new KafkaTemplate<>(producers), topics, broker, json,
                "enterprise-shell", Optional::empty, eventType -> Optional.empty(), null, MessageTracing.NOOP);
            MemberSeenEvent event = new MemberSeenEvent();

            bus.publishIntegration(event);

            assertThat(admin.describeTopics(Set.of("direct-brix.event.MemberSeen")).allTopicNames().get()
                .get("direct-brix.event.MemberSeen").partitions()).hasSize(3);
            assertThat(admin.listTopics().names().get()).noneMatch(topic -> topic.contains("integration."));
            ConsumerRecord<String, String> record = firstRecord(client, "direct-brix.event.MemberSeen");
            assertThat(record.key()).isEqualTo("member:7");
            Map<String, Object> envelope = json.readValue(record.value(), new TypeReference<>() {
            });
            assertThat(envelope).containsEntry("eventId", event.getEventId())
                .containsEntry("eventType", "MemberSeen")
                .containsEntry("reliability", "BEST_EFFORT")
                .containsEntry("producerPluginId", "conformance-publisher")
                .containsEntry("tenantId", 910001)
                .containsEntry("payload", Map.of("memberId", 7));
        } finally {
            producers.destroy();
        }
    }

    private static ConsumerRecord<String, String> firstRecord(Map<String, Object> client, String topic) {
        Map<String, Object> consumer = new HashMap<>(client);
        consumer.put(ConsumerConfig.GROUP_ID_CONFIG, "direct-bus-test");
        consumer.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> reader = new KafkaConsumer<>(consumer)) {
            reader.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : reader.poll(Duration.ofMillis(200))) {
                    return record;
                }
            }
        }
        throw new AssertionError("no record arrived on " + topic);
    }
}
