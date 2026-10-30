/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.util.regex.Pattern;

import io.runtime.orchestrator.event.ConsumedSubscription;

/**
 * Names of the broker resources of the canonical event bus (Kafka design §11.2, D18, D19): a topic per
 * event type, and a parking topic and a consumer group per subscription. The outbox relay and the direct
 * bus write the same canonical envelope to the same event topic (§13 K2).
 */
public final class KafkaEventTopics {

    private static final Pattern LEGAL = Pattern.compile("[a-zA-Z0-9._-]+");
    private static final int MAX_LENGTH = 249;

    private final String prefix;

    /**
     * Creates the names with an environment prefix.
     *
     * @param prefix prefix of every name, such as {@code staging-}; empty for none
     */
    public KafkaEventTopics(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
        if (!this.prefix.isEmpty() && !LEGAL.matcher(this.prefix).matches()) {
            throw new IllegalArgumentException("Not a Kafka topic prefix: " + prefix);
        }
    }

    /**
     * Returns the topic of an event type, such as {@code brix.event.TenantFirstOwnerAccepted}.
     *
     * @param eventType the event type
     * @return its topic
     * @throws IllegalArgumentException when the name is not a legal topic name
     */
    public String eventTopic(String eventType) {
        return legal(prefix + "brix.event." + eventType);
    }

    /**
     * Returns the topic of a command type, such as {@code brix.command.ReserveStock} (Kafka design §14 K22).
     *
     * @param commandType the command type
     * @return its topic
     * @throws IllegalArgumentException when the name is not a legal topic name
     */
    public String commandTopic(String commandType) {
        return legal(prefix + "brix.command." + commandType);
    }

    /**
     * Returns the topic a subscription consumes: its event type's, or its command type's.
     *
     * @param subscription the subscription
     * @return its topic
     */
    public String topicOf(ConsumedSubscription subscription) {
        return subscription.command() ? commandTopic(subscription.eventType()) : eventTopic(subscription.eventType());
    }

    /**
     * Returns the parking topic of a subscription, such as
     * {@code brix.parked.platform-audit.identity-audit-fact-record.v1}.
     *
     * @param subscription the subscription
     * @return its parking topic
     */
    public String parkingTopic(ConsumedSubscription subscription) {
        return legal(prefix + "brix.parked." + subscription.name());
    }

    /**
     * Returns the consumer group of a subscription: each subscription has its own, so one that fails holds
     * back no other (D19).
     *
     * @param subscription the subscription
     * @return its consumer group
     */
    public String consumerGroup(ConsumedSubscription subscription) {
        return legal(prefix + "brix.subscription." + subscription.name());
    }

    private static String legal(String name) {
        if (!LEGAL.matcher(name).matches() || name.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Not a legal Kafka name: " + name);
        }
        return name;
    }
}
