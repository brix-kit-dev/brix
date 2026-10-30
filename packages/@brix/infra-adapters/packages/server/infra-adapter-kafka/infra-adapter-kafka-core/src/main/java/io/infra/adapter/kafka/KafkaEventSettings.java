/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.time.Duration;

/**
 * Settings of the canonical event bus on Kafka (Kafka design §4.4, §11.2).
 *
 * @param partitions partitions of each event topic; the order of one partition key holds inside a partition
 * @param replicationFactor replicas of each topic
 * @param minInsyncReplicas replicas that must have a write before it counts as written; at most the replicas
 * @param retention how long an event topic keeps its records
 * @param maxPollRecords records a subscription's consumer fetches at most per poll
 * @param pollTimeout how long a consumer waits for records per poll, and so how soon it notices a stop
 * @param readyCacheTtl how long a reachability probe is trusted
 * @param adminTimeout how long a call to the broker's admin interface may take
 * @param sendTimeout how long a parking write or release may take
 */
public record KafkaEventSettings(
        int partitions,
        short replicationFactor,
        short minInsyncReplicas,
        Duration retention,
        int maxPollRecords,
        Duration pollTimeout,
        Duration readyCacheTtl,
        Duration adminTimeout,
        Duration sendTimeout) {

    /**
     * Creates and validates the settings.
     */
    public KafkaEventSettings {
        if (partitions <= 0) {
            throw new IllegalArgumentException("partitions must be positive");
        }
        if (replicationFactor <= 0) {
            throw new IllegalArgumentException("replicationFactor must be positive");
        }
        if (minInsyncReplicas <= 0 || minInsyncReplicas > replicationFactor) {
            throw new IllegalArgumentException("minInsyncReplicas must be between 1 and the replication factor ("
                + replicationFactor + "), not " + minInsyncReplicas);
        }
        if (maxPollRecords <= 0) {
            throw new IllegalArgumentException("maxPollRecords must be positive");
        }
        requirePositive(retention, "retention");
        requirePositive(pollTimeout, "pollTimeout");
        requirePositive(readyCacheTtl, "readyCacheTtl");
        requirePositive(adminTimeout, "adminTimeout");
        requirePositive(sendTimeout, "sendTimeout");
    }

    /**
     * Returns the settings of a small host: 3 partitions, one replica, 7 days.
     *
     * @return default settings
     */
    public static KafkaEventSettings defaults() {
        return new KafkaEventSettings(3, (short) 1, (short) 1, Duration.ofDays(7), 10, Duration.ofMillis(500),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10));
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
