/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import io.runtime.orchestrator.event.EventSubscriptionTransport;
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

    private KafkaHarness current;

    @Override
    protected Harness harness(ConformanceWorld world) {
        current = new KafkaHarness(world, "t" + UUID.randomUUID().toString().substring(0, 8) + "-");
        return current;
    }

    @Test
    void aSecondHostJoiningAndLeavingTheGroupNeitherLosesNorDoublesAnEvent() {
        // Kafka design §14 K25: when a host joins or leaves, the group rebalances and partitions move between the
        // hosts' consumers. A record that was in flight may arrive at its new owner again; its inbox skips it. No
        // event is lost, none takes effect twice, and each key keeps its order.
        current.start();
        for (int i = 1; i <= 10; i++) {
            world.publish("event-" + i, "member-" + (i % 5), ConformanceWorld.TENANT, i);
        }
        current.startAnotherHost();
        for (int i = 11; i <= 20; i++) {
            world.publish("event-" + i, "member-" + (i % 5), ConformanceWorld.TENANT, i);
        }
        await("the first twenty are handled", () -> world.handledIds().size() == 20);

        current.stopAnotherHost();
        for (int i = 21; i <= 30; i++) {
            world.publish("event-" + i, "member-" + (i % 5), ConformanceWorld.TENANT, i);
        }
        await("all thirty are handled", () -> world.handledIds().size() == 30);
        settle();

        assertEquals(30, Set.copyOf(world.handledIds()).size());
        for (int key = 0; key < 5; key++) {
            int k = key;
            List<Long> order = world.handled().stream().map(ConformanceWorld.Handled::memberId)
                .filter(member -> member % 5 == k).toList();
            assertEquals(order.stream().sorted().toList(), order, "the events of member-" + k + " in order");
        }
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
        private OutboxRelayManagedResource secondRelay;
        private EventSubscriptionGroup group;
        private OutboxTransport gated;
        private final Map<String, Object> client;
        private final KafkaTemplate<String, String> template;
        private final KafkaEventTopics topics;
        private final KafkaEventSettings settings;
        private final ObjectMapper json;
        private KafkaEventSubscriptionTransport anotherHostsSubscriptions;
        private EventSubscriptionGroup anotherHostsGroup;

        KafkaHarness(ConformanceWorld world, String prefix) {
            this.world = world;
            client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
            Map<String, Object> producer = new HashMap<>(client);
            producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer.put(ProducerConfig.ACKS_CONFIG, "all");
            producer.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            producers = new DefaultKafkaProducerFactory<>(producer);
            template = new KafkaTemplate<>(producers);
            topics = new KafkaEventTopics(prefix);
            settings = new KafkaEventSettings(3, (short) 1, (short) 1, Duration.ofDays(1), 10,
                Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(10));
            json = JsonMapper.builder().configureForJackson2()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
            broker = new KafkaEventBroker(Admin.create(client), topics, settings, Clock.systemUTC());
            sender = new CanonicalKafkaOutboxTransport(template, topics, broker, json, Duration.ofSeconds(10));
            subscriptions = new KafkaEventSubscriptionTransport(broker, topics, settings, client, template, json,
                Clock.systemUTC());
        }

        @Override
        public void start() {
            delivery = world.newDelivery();
            gated = new OutboxTransport() {
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
            group = group(subscriptions);
            group.start();
            relay = new OutboxRelayManagedResource(new OutboxRelay("conformance", world.outbox(), gated, world.policy(),
                Clock.systemUTC(), OutboxRelayMetrics.NOOP,
                world.tracing()), Duration.ofMillis(20), "conformance-relay");
            relay.start();
        }

        private EventSubscriptionGroup group(KafkaEventSubscriptionTransport transport) {
            OutboxRelayProperties properties = new OutboxRelayProperties();
            properties.setOwner("conformance");
            properties.setTransport("kafka");
            properties.setPollDelay(Duration.ofMillis(50));
            properties.setLeaseDuration(Duration.ofSeconds(10));
            return new EventSubscriptionGroup(properties, world.policy(), () -> delivery, () -> transport,
                EventSubscriptionMetricsFactory.NOOP);
        }

        @Override
        public void startAnotherRelay() {
            secondRelay = new OutboxRelayManagedResource(new OutboxRelay("conformance", world.outbox(), gated,
                world.policy(), Clock.systemUTC(), OutboxRelayMetrics.NOOP, world.tracing()), Duration.ofMillis(20),
                "conformance-relay-2");
            secondRelay.start();
        }

        /** Starts the consumers of a second host: they join each subscription's group, which rebalances. */
        void startAnotherHost() {
            anotherHostsSubscriptions = new KafkaEventSubscriptionTransport(broker, topics, settings, client, template,
                json, Clock.systemUTC());
            anotherHostsGroup = group(anotherHostsSubscriptions);
            anotherHostsGroup.start();
        }

        /** Stops the second host's consumers: they leave the groups, which rebalance again. */
        void stopAnotherHost() {
            anotherHostsGroup.stop();
            anotherHostsSubscriptions.close();
        }

        @Override
        public void stop() {
            if (relay != null) {
                relay.stop();
                relay = null;
            }
            if (secondRelay != null) {
                secondRelay.stop();
                secondRelay = null;
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

        @Override
        public EventSubscriptionTransport subscriptions() {
            return subscriptions;
        }

        @Override
        public void sendAtOnce(CanonicalOutboxMessage message, int copies) {
            atOnce(copies, () -> sender.publish(message));
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
