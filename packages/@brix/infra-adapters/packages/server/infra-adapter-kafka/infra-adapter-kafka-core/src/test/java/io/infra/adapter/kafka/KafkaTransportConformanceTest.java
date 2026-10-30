/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventSubscriptionGroup;
import io.runtime.orchestrator.event.EventSubscriptionMetricsFactory;
import io.runtime.orchestrator.event.EventsEndpoint;
import io.runtime.orchestrator.event.conformance.ConformanceWorld;
import io.runtime.orchestrator.event.conformance.EventTransportConformance;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxRelay;
import io.runtime.orchestrator.outbox.OutboxRelayManagedResource;
import io.runtime.orchestrator.outbox.OutboxRelayMetrics;
import io.runtime.orchestrator.outbox.OutboxRelayProperties;
import io.runtime.orchestrator.outbox.OutboxTransport;

/**
 * Kafka passes the transport conformance suite (Kafka design §4.6, §11.2): the relay sends each event to its
 * topic, the subscription's consumer group receives it, and the delivery core hands it to the subscriber.
 * The broker is an embedded single-node KRaft cluster that creates no topic by itself; each test uses its
 * own topic prefix, so no test sees another's topics or groups.
 */
class KafkaTransportConformanceTest extends EventTransportConformance {

    private static EmbeddedKafkaBroker kafka;

    @BeforeAll
    static void startKafka() {
        kafka = new EmbeddedKafkaKraftBroker(1, 3).brokerProperties(Map.of(
            "auto.create.topics.enable", "false",
            "group.initial.rebalance.delay.ms", "0"));
        kafka.afterPropertiesSet();
    }

    @AfterAll
    static void stopKafka() {
        kafka.destroy();
    }

    @Override
    protected Duration settleTime() {
        return Duration.ofSeconds(1);
    }

    @Override
    protected Harness harness(ConformanceWorld world) {
        return new KafkaHarness(world, "t" + UUID.randomUUID().toString().substring(0, 8) + "-");
    }

    private static final class KafkaHarness implements Harness {

        private final ConformanceWorld world;
        private final DefaultKafkaProducerFactory<String, String> producers;
        private final KafkaEventBroker broker;
        private final CanonicalKafkaOutboxTransport sender;
        private final KafkaEventSubscriptionTransport subscriptions;
        private volatile boolean reachable = true;
        private volatile EventDelivery delivery;
        private OutboxRelayManagedResource relay;
        private EventSubscriptionGroup group;

        KafkaHarness(ConformanceWorld world, String prefix) {
            this.world = world;
            Map<String, Object> client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
            Map<String, Object> producer = new HashMap<>(client);
            producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer.put(ProducerConfig.ACKS_CONFIG, "all");
            producer.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            producers = new DefaultKafkaProducerFactory<>(producer);
            KafkaTemplate<String, String> template = new KafkaTemplate<>(producers);
            KafkaEventTopics topics = new KafkaEventTopics(prefix);
            KafkaEventSettings settings = new KafkaEventSettings(3, (short) 1, Duration.ofDays(1), 10, Duration.ofMillis(100),
                Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(10));
            ObjectMapper json = JsonMapper.builder().configureForJackson2()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
            broker = new KafkaEventBroker(Admin.create(client), topics, settings, Clock.systemUTC());
            sender = new CanonicalKafkaOutboxTransport(template, topics, broker, json, Duration.ofSeconds(10));
            subscriptions = new KafkaEventSubscriptionTransport(broker, topics, settings, client, template, json,
                Clock.systemUTC());
        }

        @Override
        public void start() {
            delivery = world.newDelivery();
            OutboxTransport gated = new OutboxTransport() {
                @Override
                public void publish(CanonicalOutboxMessage message) {
                    sender.publish(message);
                }

                @Override
                public boolean ready() {
                    // Stands for a broker the relay cannot reach: the sender's own probe answers the same way.
                    return reachable && sender.ready();
                }
            };
            OutboxRelayProperties properties = new OutboxRelayProperties();
            properties.setOwner("conformance");
            properties.setTransport("kafka");
            properties.setPollDelay(Duration.ofMillis(50));
            properties.setLeaseDuration(Duration.ofSeconds(10));
            group = new EventSubscriptionGroup(properties, world.policy(), () -> delivery, () -> subscriptions,
                EventSubscriptionMetricsFactory.NOOP);
            group.start();
            relay = new OutboxRelayManagedResource(new OutboxRelay("conformance", world.outbox(), gated, world.policy(),
                Clock.systemUTC(), OutboxRelayMetrics.NOOP,
                world.tracing()), Duration.ofMillis(20), "conformance-relay");
            relay.start();
        }

        @Override
        public void stop() {
            if (relay != null) {
                relay.stop();
                relay = null;
            }
            if (group != null) {
                group.stop();
                group = null;
            }
        }

        @Override
        public void reachable(boolean reachable) {
            this.reachable = reachable;
        }

        @Override
        public List<Parked> parked() {
            return subscriptions.parked(100).values().stream()
                .flatMap(List::stream)
                .map(event -> new Parked(event.eventId(), event.errorCode()))
                .toList();
        }

        @Override
        public boolean replay(String eventId) {
            return subscriptionOf(eventId)
                .map(subscription -> endpoint().replay(subscription, eventId, OPERATOR, "conformance replay",
                    SecurityContext.NONE).getStatus() == 200)
                .orElse(false);
        }

        @Override
        public boolean discard(String eventId) {
            return subscriptionOf(eventId)
                .map(subscription -> endpoint().discard(subscription, eventId, OPERATOR, "conformance discard",
                    SecurityContext.NONE).getStatus() == 200)
                .orElse(false);
        }

        @Override
        public ConformanceWorld world() {
            return world;
        }

        private EventsEndpoint endpoint() {
            return new EventsEndpoint(() -> subscriptions, () -> delivery, world::audit);
        }

        /** The subscription that parked the event, as the endpoint lists it. */
        private Optional<String> subscriptionOf(String eventId) {
            return subscriptions.parked(100).entrySet().stream()
                .filter(parked -> parked.getValue().stream().anyMatch(event -> event.eventId().equals(eventId)))
                .map(Map.Entry::getKey)
                .findFirst();
        }

        @Override
        public void close() {
            stop();
            subscriptions.close();
            broker.close();
            producers.destroy();
        }
    }
}
