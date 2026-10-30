/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;

/**
 * The connection settings every Kafka client of the adapter starts from: the brokers, TLS and the client identity
 * (root BRX-RS-SECURITY-006, BOOT-003; Kafka design D35, §14 K24).
 *
 * <p>The host passes its TLS and identity settings through unchanged: {@code security.protocol}, {@code ssl.*} and
 * {@code sasl.*}. Any other client setting is refused, because the adapter owns the producer, consumer and admin
 * settings its delivery guarantees rest on, such as idempotence, acknowledgements, offsets and timeouts.</p>
 *
 * <p>Under the production profile the adapter refuses to start unless its clients use TLS and present a client
 * certificate, the broker's host name is verified, and topics are created with at least
 * {@value #PRODUCTION_REPLICAS} replicas of which at least {@value #PRODUCTION_MIN_INSYNC} must have each write. A
 * shared secret such as SASL/SCRAM is not a client identity for production (D35).</p>
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
public final class KafkaClientSettings {

    /** Replicas of each topic the production profile needs at least. */
    public static final int PRODUCTION_REPLICAS = 3;

    /** Replicas that must have each write under the production profile, at least. */
    public static final int PRODUCTION_MIN_INSYNC = 2;

    private static final List<String> PASS_THROUGH_PREFIXES = List.of("ssl.", "sasl.");

    private final Map<String, Object> configs;

    private KafkaClientSettings(Map<String, Object> configs) {
        this.configs = Map.copyOf(configs);
    }

    /**
     * Builds the settings and checks them.
     *
     * @param bootstrapServers the brokers
     * @param passThrough the host's TLS and identity settings, by Kafka client setting name
     * @param events the topics' settings
     * @param production whether the host runs the production profile
     * @return the settings
     * @throws IllegalStateException when a setting may not be passed through, or the production profile refuses the
     *         configuration
     */
    public static KafkaClientSettings of(
            String bootstrapServers,
            Map<String, String> passThrough,
            KafkaEventSettings events,
            boolean production) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalStateException("brix.infra.kafka.bootstrap-servers must name the brokers");
        }
        Objects.requireNonNull(events, "events must not be null");
        Map<String, Object> configs = new LinkedHashMap<>();
        List<String> refused = new ArrayList<>();
        (passThrough == null ? Map.<String, String>of() : passThrough).forEach((name, value) -> {
            if (passesThrough(name)) {
                configs.put(name, value);
            } else {
                refused.add(name);
            }
        });
        if (!refused.isEmpty()) {
            throw new IllegalStateException("brix.infra.kafka.client passes only security.protocol, ssl.* and sasl.* to"
                + " the Kafka clients; the adapter owns " + refused + " (Kafka design §14 K24)");
        }
        configs.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        if (production) {
            requireProductionGrade(configs, events);
        }
        return new KafkaClientSettings(configs);
    }

    /**
     * Returns the settings a client starts from; the client adds its own.
     *
     * @return client settings by name
     */
    public Map<String, Object> configs() {
        return configs;
    }

    /**
     * Returns the security protocol the clients use.
     *
     * @return {@code PLAINTEXT} unless the host sets another
     */
    public String securityProtocol() {
        return String.valueOf(configs.getOrDefault(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
            CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL));
    }

    private static boolean passesThrough(String name) {
        return CommonClientConfigs.SECURITY_PROTOCOL_CONFIG.equals(name)
            || PASS_THROUGH_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static void requireProductionGrade(Map<String, Object> configs, KafkaEventSettings events) {
        List<String> problems = new ArrayList<>();
        String protocol = String.valueOf(configs.getOrDefault(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
            CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL));
        if (!SecurityProtocol.SSL.name.equalsIgnoreCase(protocol)) {
            problems.add("security.protocol must be SSL, TLS with a client certificate, not " + protocol);
        }
        boolean keystore = present(configs, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG)
            || present(configs, SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG)
                && present(configs, SslConfigs.SSL_KEYSTORE_KEY_CONFIG);
        if (!keystore) {
            problems.add("the client certificate is missing: set ssl.keystore.location, or ssl.keystore.certificate.chain"
                + " with ssl.keystore.key");
        }
        if (configs.containsKey(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)
                && !present(configs, SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)) {
            problems.add("ssl.endpoint.identification.algorithm must not be empty: the broker's host name is verified");
        }
        if (events.replicationFactor() < PRODUCTION_REPLICAS) {
            problems.add("brix.infra.kafka.events.replication-factor must be at least " + PRODUCTION_REPLICAS + ", not "
                + events.replicationFactor());
        }
        if (events.minInsyncReplicas() < PRODUCTION_MIN_INSYNC) {
            problems.add("brix.infra.kafka.events.min-insync-replicas must be at least " + PRODUCTION_MIN_INSYNC
                + ", not " + events.minInsyncReplicas());
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The production profile refuses this Kafka configuration (root"
                + " BRX-RS-BOOT-003, SECURITY-006; Kafka design D35): " + String.join("; ", problems));
        }
    }

    private static boolean present(Map<String, Object> configs, String name) {
        Object value = configs.get(name);
        return value != null && !String.valueOf(value).isBlank();
    }
}
