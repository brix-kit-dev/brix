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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.event.ConsumedSubscription;

/**
 * The broker as the event bus sees it: whether it can be reached, and the topics the declared events need
 * (Kafka design §4.3, §4.4, D18, D21).
 * <p>Reachability is probed with the cluster metadata and trusted for a few seconds, so the relays that ask
 * on every pass do not each go to the broker. Topics are created from the manifests: an event topic per
 * event type with the configured partitions and retention, and a compacted parking topic per subscription,
 * so a parked event stays until it is released. The broker's own automatic topic creation stays off, so a
 * misspelt topic never appears by itself.</p>
 */
public final class KafkaEventBroker implements AutoCloseable {

    /** How long a reachability probe may take. */
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private static final Logger log = LoggerFactory.getLogger(KafkaEventBroker.class);

    private final Admin admin;
    private final KafkaEventTopics topics;
    private final KafkaEventSettings settings;
    private final Clock clock;
    private final Set<String> ensured = ConcurrentHashMap.newKeySet();
    private Instant probedAt;
    private Boolean reachable;

    /**
     * Creates the broker view.
     *
     * @param admin the broker's admin client; closed with this view
     * @param topics resource names
     * @param settings partitions, replicas, retention and timeouts
     * @param clock probe clock
     */
    public KafkaEventBroker(Admin admin, KafkaEventTopics topics, KafkaEventSettings settings, Clock clock) {
        this.admin = Objects.requireNonNull(admin, "admin must not be null");
        this.topics = Objects.requireNonNull(topics, "topics must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Whether the broker answers with its cluster metadata. The answer is trusted for
     * {@link KafkaEventSettings#readyCacheTtl()}.
     *
     * @return reachability
     */
    public synchronized boolean reachable() {
        Instant now = clock.instant();
        if (probedAt != null && now.isBefore(probedAt.plus(settings.readyCacheTtl()))) {
            return reachable;
        }
        boolean answered;
        try {
            answered = !admin.describeCluster(new DescribeClusterOptions().timeoutMs((int) PROBE_TIMEOUT.toMillis()))
                .nodes().get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).isEmpty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            answered = false;
        } catch (ExecutionException | TimeoutException | KafkaException unreachable) {
            answered = false;
        }
        if (reachable == null || reachable != answered) {
            log.info("event=kafka.broker_reachability reachable={}", answered);
        }
        reachable = answered;
        probedAt = now;
        return answered;
    }

    /**
     * Creates the topics of these event types that do not exist yet.
     *
     * @param eventTypes event types
     * @throws IllegalStateException when the broker cannot create them now
     */
    public void ensureEventTopics(Collection<String> eventTypes) {
        ensureMessageTopics(eventTypes.stream().map(topics::eventTopic).toList());
    }

    /**
     * Makes sure every command type has its topic (Kafka design §14 K22), as event types have theirs.
     *
     * @param commandTypes the command types
     */
    public void ensureCommandTopics(Collection<String> commandTypes) {
        ensureMessageTopics(commandTypes.stream().map(topics::commandTopic).toList());
    }

    private void ensureMessageTopics(Collection<String> names) {
        Map<String, NewTopic> wanted = new TreeMap<>();
        for (String name : names) {
            wanted.put(name, new NewTopic(name, settings.partitions(), settings.replicationFactor())
                .configs(Map.of(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(settings.retention().toMillis()))));
        }
        ensure(wanted);
    }

    /**
     * Creates the parking topics of these subscriptions that do not exist yet: one partition, compacted.
     *
     * @param subscriptions subscriptions
     * @throws IllegalStateException when the broker cannot create them now
     */
    public void ensureParkingTopics(Collection<ConsumedSubscription> subscriptions) {
        Map<String, NewTopic> wanted = new TreeMap<>();
        for (ConsumedSubscription subscription : subscriptions) {
            String name = topics.parkingTopic(subscription);
            wanted.put(name, new NewTopic(name, 1, settings.replicationFactor())
                .configs(Map.of(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)));
        }
        ensure(wanted);
    }

    /**
     * Forgets the last reachability answer, so the next {@link #reachable()} asks the broker again; the
     * transport calls it when a send times out or the connection fails.
     */
    public synchronized void suspect() {
        probedAt = null;
    }

    @Override
    public void close() {
        admin.close(Duration.ofSeconds(5));
    }

    private void ensure(Map<String, NewTopic> wanted) {
        wanted.keySet().removeIf(ensured::contains);
        if (wanted.isEmpty()) {
            return;
        }
        long timeout = settings.adminTimeout().toMillis();
        try {
            Set<String> existing = admin.listTopics(new ListTopicsOptions().timeoutMs((int) timeout)).names()
                .get(timeout, TimeUnit.MILLISECONDS);
            List<NewTopic> missing = wanted.values().stream().filter(topic -> !existing.contains(topic.name())).toList();
            if (!missing.isEmpty()) {
                CreateTopicsResult created = admin.createTopics(missing, new CreateTopicsOptions().timeoutMs((int) timeout));
                for (NewTopic topic : missing) {
                    try {
                        created.values().get(topic.name()).get(timeout, TimeUnit.MILLISECONDS);
                    } catch (ExecutionException failure) {
                        if (!(failure.getCause() instanceof TopicExistsException)) {
                            throw failure;
                        }
                    }
                }
                log.info("event=kafka.topics_created topics={}", missing.stream().map(NewTopic::name).toList());
            }
            ensured.addAll(wanted.keySet());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while preparing Kafka topics " + wanted.keySet(), interrupted);
        } catch (ExecutionException | TimeoutException | KafkaException failure) {
            Throwable cause = failure instanceof ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
            throw new IllegalStateException("Kafka topics " + wanted.keySet() + " could not be prepared", cause);
        }
    }
}
