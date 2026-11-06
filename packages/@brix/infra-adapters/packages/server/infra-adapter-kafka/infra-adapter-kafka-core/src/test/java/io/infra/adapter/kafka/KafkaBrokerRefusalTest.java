/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import tools.jackson.databind.json.JsonMapper;

import io.infra.adapter.kafka.config.KafkaAutoConfiguration;
import io.infra.adapter.kafka.config.KafkaEventBusProperties;
import io.runtime.orchestrator.event.conformance.InMemoryOutboxStore;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxRelay;
import io.runtime.orchestrator.outbox.OutboxRelayPolicy;
import io.runtime.orchestrator.outbox.OutboxTransportException;

/**
 * Standard-link plan §10.28, on a broker with the adapter's own producer: what Kafka refuses reaches the relay as what
 * it is. Only a real broker shows this, because the failures take their shape in the producer and in KafkaTemplate. The
 * broker has an authorizer that lets everyone in until a test denies something.
 */
class KafkaBrokerRefusalTest {

    private static final String ANYONE_WITHOUT_AN_IDENTITY = "User:ANONYMOUS";

    private static EmbeddedKafkaBroker kafka;

    @BeforeAll
    static void startKafka() {
        kafka = new EmbeddedKafkaKraftBroker(1, 3).brokerProperties(Map.of(
            "auto.create.topics.enable", "false",
            "authorizer.class.name", "org.apache.kafka.metadata.authorizer.StandardAuthorizer",
            "allow.everyone.if.no.acl.found", "true"));
        kafka.afterPropertiesSet();
    }

    @AfterAll
    static void stopKafka() {
        kafka.destroy();
    }

    @Test
    void aRecordTooLargeIsRefusedForGood() {
        try (Sender sender = new Sender("large-")) {
            OutboxTransportException refused = sender.refusalOf("OrderPlaced", "{\"x\":\"" + "y".repeat(2_000_000) + "\"}",
                "KAFKA_RECORD_REJECTED");

            assertThat(refused.permanent()).isTrue();
        }
    }

    @Test
    void aWriteTheBrokerDeniesWaitsAndGoesOnceTheAclIsGone() throws Exception {
        try (Sender sender = new Sender("denied-")) {
            sender.broker.ensureEventTopics(Set.of("OrderPlaced"));
            AclBinding denial = sender.deny("OrderPlaced", AclOperation.WRITE);

            OutboxTransportException refused = sender.refusalOf("OrderPlaced", "{}", "KAFKA_ACCESS_DENIED");

            assertThat(refused.unavailable()).isTrue();
            assertThat(sender.transport.ready()).as("the cluster still answers").isTrue();

            sender.allow(denial);
            sender.acceptedAfterAWhile("OrderPlaced");
        }
    }

    @Test
    void aTopicTheClientMayNotEvenSeeWaitsToo() throws Exception {
        // The producer finds this one before it queues the record, so KafkaTemplate throws it from send().
        try (Sender sender = new Sender("hidden-")) {
            sender.broker.ensureEventTopics(Set.of("OrderPlaced"));
            sender.deny("OrderPlaced", AclOperation.DESCRIBE);
            sender.deny("OrderPlaced", AclOperation.WRITE);

            OutboxTransportException refused = sender.refusalOf("OrderPlaced", "{}", "KAFKA_ACCESS_DENIED");

            assertThat(refused.unavailable()).isTrue();
        }
    }

    @Test
    void aTopicDeletedBehindTheTransportsBackWaits() throws Exception {
        // The transport remembers the topics it prepared, so it does not create this one again.
        try (Sender sender = new Sender("deleted-")) {
            sender.acceptedAfterAWhile("OrderPlaced");
            sender.admin.deleteTopics(List.of(sender.topics.eventTopic("OrderPlaced"))).all().get();

            OutboxTransportException refused = sender.refusalOf("OrderPlaced", "{}", "KAFKA_SEND_TIMEOUT",
                "KAFKA_BROKER_UNAVAILABLE");

            assertThat(refused.unavailable()).isTrue();
        }
    }

    @Test
    void aRefusedTypeWaitsWithoutSpendingAttemptsWhileTheOthersGoAndLeavesOnceTheBrokerTakesIt() throws Exception {
        try (Sender sender = new Sender("relay-")) {
            sender.broker.ensureEventTopics(Set.of("Refused", "Taken"));
            AclBinding denial = sender.deny("Refused", AclOperation.WRITE);
            sender.refusalOf("Refused", "{}", "KAFKA_ACCESS_DENIED");
            sender.acceptedAfterAWhile("Taken");
            InMemoryOutboxStore outbox = new InMemoryOutboxStore();
            OutboxRelayPolicy twoAttempts = new OutboxRelayPolicy(25, 2, Duration.ofSeconds(30), Duration.ofMillis(100),
                Duration.ZERO);
            OutboxRelay relay = new OutboxRelay("relay", outbox, sender.transport, twoAttempts, Clock.systemUTC());
            CanonicalOutboxMessage first = message("Refused", "key-1");
            CanonicalOutboxMessage second = message("Refused", "key-2");
            CanonicalOutboxMessage other = message("Taken", "key-3");
            List.of(first, second, other).forEach(outbox::append);

            for (int pass = 0; pass < 4; pass++) {
                relay.processDueBatch();
                Thread.sleep(1100);
            }

            assertThat(status(outbox, other)).isEqualTo("PUBLISHED");
            assertThat(status(outbox, first)).as("four refusals of two attempts park nothing").isEqualTo("PENDING");
            assertThat(status(outbox, second)).isEqualTo("PENDING");
            assertThat(outbox.attempts(first.eventId())).isZero();
            assertThat(outbox.attempts(second.eventId())).isZero();

            sender.allow(denial);
            sender.acceptedAfterAWhile("Refused");
            for (int pass = 0; pass < 10 && !"PUBLISHED".equals(status(outbox, second)); pass++) {
                relay.processDueBatch();
                Thread.sleep(1100);
            }

            assertThat(status(outbox, first)).isEqualTo("PUBLISHED");
            assertThat(status(outbox, second)).isEqualTo("PUBLISHED");
            assertThat(outbox.attempts(first.eventId())).isEqualTo(1);
            assertThat(outbox.listParked(10)).isEmpty();
        }
    }

    private static String status(InMemoryOutboxStore outbox, CanonicalOutboxMessage message) {
        return outbox.find(message.eventId()).orElseThrow().status();
    }

    private static CanonicalOutboxMessage message(String type, String partitionKey) {
        return message(type, partitionKey, "{\"tenantId\":100}");
    }

    private static CanonicalOutboxMessage message(String type, String partitionKey, String payload) {
        String id = UUID.randomUUID().toString();
        return new CanonicalOutboxMessage(id, "event-" + id, "EVENT", type, "1.0.0", "CRITICAL", "platform-tenant",
            "TENANT", 100L, partitionKey, OffsetDateTime.parse("2026-07-28T01:00:00Z"), "correlation-" + id, null, null,
            null, payload, 1);
    }

    /** The transport on the adapter's own producer settings, with a short publish timeout. */
    private static final class Sender implements AutoCloseable {

        private final Admin admin;
        private final KafkaEventTopics topics;
        private final KafkaEventBroker broker;
        private final ProducerFactory<String, String> producers;
        private final CanonicalKafkaOutboxTransport transport;

        Sender(String prefix) {
            Map<String, Object> client = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
            KafkaEventSettings settings = new KafkaEventSettings(3, (short) 1, (short) 1, Duration.ofDays(1), 10,
                Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(10));
            KafkaEventBusProperties properties = new KafkaEventBusProperties();
            properties.getOutbox().setPublishTimeoutMs(2000);
            producers = new KafkaAutoConfiguration().kafkaProducerFactory(properties,
                KafkaClientSettings.of(kafka.getBrokersAsString(), Map.of(), settings, false));
            admin = Admin.create(client);
            topics = new KafkaEventTopics(prefix);
            broker = new KafkaEventBroker(Admin.create(client), topics, settings, Clock.systemUTC());
            transport = new CanonicalKafkaOutboxTransport(new KafkaTemplate<>(producers), topics, broker,
                JsonMapper.builder().configureForJackson2().build(), Duration.ofSeconds(2));
        }

        AclBinding deny(String type, AclOperation operation) throws Exception {
            AclBinding denial = new AclBinding(
                new ResourcePattern(ResourceType.TOPIC, topics.eventTopic(type), PatternType.LITERAL),
                new AccessControlEntry(ANYONE_WITHOUT_AN_IDENTITY, "*", operation, AclPermissionType.DENY));
            admin.createAcls(List.of(denial)).all().get();
            return denial;
        }

        void allow(AclBinding denial) throws Exception {
            admin.deleteAcls(List.of(denial.toFilter())).all().get();
        }

        /**
         * How Kafka refuses a record of the type, once the broker has applied what the test changed. A busy machine may
         * time out on a new topic before that, so only the expected refusals end the wait.
         */
        OutboxTransportException refusalOf(String type, String payload, String... expectedCodes) {
            long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            String last = "none";
            while (System.nanoTime() < until) {
                try {
                    transport.publish(message(type, "key-1", payload));
                } catch (OutboxTransportException refused) {
                    if (List.of(expectedCodes).contains(refused.errorCode())) {
                        return refused;
                    }
                    last = refused.errorCode();
                }
                pause();
            }
            throw new AssertionError("Kafka never refused " + type + " with " + List.of(expectedCodes) + "; last " + last);
        }

        void acceptedAfterAWhile(String type) {
            long until = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (true) {
                try {
                    transport.publish(message(type, "key-1"));
                    return;
                } catch (OutboxTransportException refused) {
                    if (System.nanoTime() > until) {
                        throw refused;
                    }
                    pause();
                }
            }
        }

        private static void pause() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }

        @Override
        public void close() {
            ((DefaultKafkaProducerFactory<String, String>) producers).destroy();
            broker.close();
            admin.close();
        }
    }
}
