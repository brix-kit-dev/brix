/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.KafkaTemplate;

import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.event.ParkedEvent;
import io.runtime.orchestrator.event.SubscriptionDelivery;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Consumes the declared subscriptions from Kafka for the runtime (Kafka design §4.2, §11.2).
 * <p>Preparing creates the event topics and the subscriptions' compacted parking topics, then starts reading
 * the parking topics. Each subscription is consumed by its own consumer group from its event type's topic,
 * reading from the earliest record when the group has no committed offset. Plugins never see any of it.</p>
 */
public final class KafkaEventSubscriptionTransport implements EventSubscriptionTransport, AutoCloseable {

    private final KafkaEventBroker broker;
    private final KafkaEventTopics topics;
    private final KafkaEventSettings settings;
    private final Map<String, Object> clientConfigs;
    private final CanonicalEnvelopeCodec envelopes;
    private final KafkaParkingLot parking;
    private final Clock clock;
    private final java.util.function.Supplier<Instant> registeredAt;

    /**
     * Creates the transport.
     *
     * @param broker the broker's reachability and topics
     * @param topics resource names
     * @param settings partitions, polling and timeouts
     * @param clientConfigs connection settings every consumer shares, such as the bootstrap servers
     * @param producer writes parking records and tombstones
     * @param objectMapper reads envelopes
     * @param clock consumer clock
     */
    public KafkaEventSubscriptionTransport(
            KafkaEventBroker broker,
            KafkaEventTopics topics,
            KafkaEventSettings settings,
            Map<String, Object> clientConfigs,
            KafkaTemplate<String, String> producer,
            ObjectMapper objectMapper,
            Clock clock) {
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.topics = Objects.requireNonNull(topics, "topics must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clientConfigs = Map.copyOf(Objects.requireNonNull(clientConfigs, "clientConfigs must not be null"));
        this.envelopes = new CanonicalEnvelopeCodec(Objects.requireNonNull(objectMapper, "objectMapper must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.parking = new KafkaParkingLot(producer, topics, () -> new KafkaConsumer<>(readerConfigs()), envelopes,
            settings, clock);
        // A subscription is registered when the host that first runs it starts: everything this host publishes is
        // later, and the minute covers clocks a little apart (Kafka design D32).
        this.registeredAt = () -> Instant.ofEpochMilli(java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime())
            .minus(Duration.ofMinutes(1));
    }

    @Override
    public boolean ready() {
        return broker.reachable();
    }

    @Override
    public void prepare(Set<String> eventTypes, List<ConsumedSubscription> subscriptions) {
        broker.ensureEventTopics(eventTypes);
        broker.ensureCommandTopics(subscriptions.stream().filter(ConsumedSubscription::command)
            .map(ConsumedSubscription::eventType).toList());
        broker.ensureParkingTopics(subscriptions);
        Map<String, String> topicsByGroup = new java.util.LinkedHashMap<>();
        subscriptions.forEach(subscription -> topicsByGroup.put(topics.consumerGroup(subscription), topics.topicOf(subscription)));
        broker.startNewGroupsAt(topicsByGroup, registeredAt.get());
        parking.watch(subscriptions);
    }

    @Override
    public Consumption consume(ConsumedSubscription subscription, SubscriptionDelivery delivery) {
        KafkaSubscriptionConsumer consumer = new KafkaSubscriptionConsumer(subscription, delivery,
            () -> new KafkaConsumer<>(consumerConfigs(subscription)), parking, envelopes,
            topics.topicOf(subscription), settings.pollTimeout(), clock);
        consumer.start();
        return consumer;
    }

    @Override
    public Map<String, List<ParkedEvent>> parked(int limit) {
        return parking.list(limit);
    }

    @Override
    public Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId) {
        return parking.find(subscription, eventId);
    }

    @Override
    public Optional<CanonicalOutboxMessage> parkedMessage(ConsumedSubscription subscription, String eventId) {
        return parking.message(subscription, eventId);
    }

    @Override
    public void release(ConsumedSubscription subscription, String eventId) {
        parking.release(subscription, eventId);
    }

    @Override
    public Duration retention() {
        return settings.retention();
    }

    @Override
    public void close() {
        parking.close();
    }

    private Map<String, Object> consumerConfigs(ConsumedSubscription subscription) {
        Map<String, Object> configs = new HashMap<>(clientConfigs);
        String group = topics.consumerGroup(subscription);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        configs.put(ConsumerConfig.CLIENT_ID_CONFIG, group);
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, settings.maxPollRecords());
        configs.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return configs;
    }

    private Map<String, Object> readerConfigs() {
        Map<String, Object> configs = new HashMap<>(clientConfigs);
        configs.put(ConsumerConfig.CLIENT_ID_CONFIG, "brix-event-parking");
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return configs;
    }
}
