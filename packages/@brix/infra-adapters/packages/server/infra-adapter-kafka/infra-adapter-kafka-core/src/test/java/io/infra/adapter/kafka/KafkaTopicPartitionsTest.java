/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/**
 * Standard-link plan point 5, on a broker: a production host refuses an event or command topic that already exists
 * with fewer than {@link KafkaClientSettings#PRODUCTION_PARTITIONS} partitions, and goes on by itself once they are
 * raised; it creates a missing topic with the configured partitions.
 */
class KafkaTopicPartitionsTest {

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
    void anExistingTopicWithTooFewPartitionsIsRefusedUntilTheyAreRaised() throws Exception {
        Map<String, Object> client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        KafkaEventTopics topics = new KafkaEventTopics("refused-");
        String topic = topics.eventTopic("OrderPlaced");
        try (Admin admin = Admin.create(client);
                KafkaEventBroker broker = new KafkaEventBroker(Admin.create(client), topics, settings(),
                    KafkaClientSettings.PRODUCTION_PARTITIONS, Clock.systemUTC())) {
            admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1))).all().get();

            assertThatThrownBy(() -> broker.ensureEventTopics(Set.of("OrderPlaced")))
                .isInstanceOf(KafkaTopicPartitionsException.class)
                .hasMessageContaining(topic + " (3)")
                .hasMessageContaining("--partitions 12");

            admin.createPartitions(Map.of(topic, NewPartitions.increaseTo(12))).all().get();

            long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (true) {
                try {
                    broker.ensureEventTopics(Set.of("OrderPlaced"));
                    break;
                } catch (KafkaTopicPartitionsException notYetSeen) {
                    if (System.nanoTime() > until) {
                        throw notYetSeen;
                    }
                    Thread.sleep(100);
                }
            }
        }
    }

    @Test
    void aMissingCommandTopicIsCreatedWithTheConfiguredPartitions() throws Exception {
        Map<String, Object> client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        KafkaEventTopics topics = new KafkaEventTopics("created-");
        String topic = topics.commandTopic("orders.cancel-order");
        try (Admin admin = Admin.create(client);
                KafkaEventBroker broker = new KafkaEventBroker(Admin.create(client), topics, settings(),
                    KafkaClientSettings.PRODUCTION_PARTITIONS, Clock.systemUTC())) {
            broker.ensureCommandTopics(Set.of("orders.cancel-order"));

            assertThat(partitionsOf(admin, topic)).isEqualTo(12);
        }
    }

    /** The topic's partitions, once the broker's metadata shows the topic it has just created. */
    private static int partitionsOf(Admin admin, String topic) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (true) {
            try {
                return admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic).partitions().size();
            } catch (java.util.concurrent.ExecutionException notYetSeen) {
                if (!(notYetSeen.getCause() instanceof org.apache.kafka.common.errors.UnknownTopicOrPartitionException)
                        || System.nanoTime() > until) {
                    throw notYetSeen;
                }
                Thread.sleep(100);
            }
        }
    }

    private static KafkaEventSettings settings() {
        return new KafkaEventSettings(12, (short) 1, (short) 1, Duration.ofDays(1), 10, Duration.ofMillis(100),
            Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(10));
    }
}
