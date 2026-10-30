/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The adapter's client settings and the production profile's floor for them (Kafka design D35, §14 K24).
 */
class KafkaClientSettingsTest {

    private static final KafkaEventSettings DURABLE = settings(3, 2);

    @Test
    void tlsAndClientIdentitySettingsReachEveryClientUnchanged() {
        KafkaClientSettings settings = KafkaClientSettings.of("kafka-1:9093,kafka-2:9093", mutualTls(),
            KafkaEventSettings.defaults(), false);

        assertThat(settings.configs())
            .containsEntry("bootstrap.servers", "kafka-1:9093,kafka-2:9093")
            .containsEntry("security.protocol", "SSL")
            .containsEntry("ssl.keystore.location", "/etc/brix/kafka/client.p12")
            .containsEntry("ssl.truststore.location", "/etc/brix/kafka/trust.p12");
        assertThat(settings.securityProtocol()).isEqualTo("SSL");
    }

    @Test
    void settingsTheAdapterOwnsAreNotPassedThrough() {
        Map<String, String> client = new LinkedHashMap<>(mutualTls());
        client.put("enable.auto.commit", "true");
        client.put("acks", "1");

        assertThatThrownBy(() -> KafkaClientSettings.of("kafka:9093", client, KafkaEventSettings.defaults(), false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("enable.auto.commit")
            .hasMessageContaining("acks");
    }

    @Test
    void withoutTlsSettingsTheClientsUsePlaintextOutsideProduction() {
        KafkaClientSettings settings = KafkaClientSettings.of("kafka:9092", Map.of(), KafkaEventSettings.defaults(),
            false);

        assertThat(settings.securityProtocol()).isEqualTo("PLAINTEXT");
        assertThat(settings.configs()).containsOnlyKeys("bootstrap.servers");
    }

    @Test
    void productionAcceptsMutualTlsWithThreeReplicasAndTwoInSync() {
        assertThat(KafkaClientSettings.of("kafka:9093", mutualTls(), DURABLE, true).securityProtocol()).isEqualTo("SSL");
    }

    @Test
    void productionAcceptsAClientCertificateGivenAsPem() {
        Map<String, String> pem = Map.of("security.protocol", "SSL", "ssl.keystore.type", "PEM",
            "ssl.keystore.certificate.chain", "-----BEGIN CERTIFICATE-----...", "ssl.keystore.key",
            "-----BEGIN PRIVATE KEY-----...");

        assertThat(KafkaClientSettings.of("kafka:9093", pem, DURABLE, true).configs())
            .containsKey("ssl.keystore.certificate.chain");
    }

    @Test
    void productionRefusesPlaintext() {
        assertThatThrownBy(() -> KafkaClientSettings.of("kafka:9092", Map.of(), DURABLE, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("production profile")
            .hasMessageContaining("security.protocol must be SSL")
            .hasMessageContaining("client certificate is missing");
    }

    @Test
    void productionRefusesASharedSecretInsteadOfAClientCertificate() {
        Map<String, String> scram = Map.of("security.protocol", "SASL_SSL", "sasl.mechanism", "SCRAM-SHA-512",
            "sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required;");

        assertThatThrownBy(() -> KafkaClientSettings.of("kafka:9093", scram, DURABLE, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not SASL_SSL");
    }

    @Test
    void productionRefusesTlsWithoutAClientCertificate() {
        Map<String, String> serverOnly = Map.of("security.protocol", "SSL", "ssl.truststore.location",
            "/etc/brix/kafka/trust.p12");

        assertThatThrownBy(() -> KafkaClientSettings.of("kafka:9093", serverOnly, DURABLE, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("client certificate is missing");
    }

    @Test
    void productionRefusesSwitchingOffTheBrokersHostNameCheck() {
        Map<String, String> unchecked = new LinkedHashMap<>(mutualTls());
        unchecked.put("ssl.endpoint.identification.algorithm", "");

        assertThatThrownBy(() -> KafkaClientSettings.of("kafka:9093", unchecked, DURABLE, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ssl.endpoint.identification.algorithm");
    }

    @Test
    void productionRefusesTopicsWithFewerThanThreeReplicasOrTwoInSync() {
        assertThatThrownBy(() -> KafkaClientSettings.of("kafka:9093", mutualTls(), settings(1, 1), true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("replication-factor must be at least 3, not 1")
            .hasMessageContaining("min-insync-replicas must be at least 2, not 1");
        assertThatThrownBy(() -> KafkaClientSettings.of("kafka:9093", mutualTls(), settings(3, 1), true))
            .hasMessageContaining("min-insync-replicas must be at least 2, not 1");
    }

    @Test
    void moreInSyncReplicasThanReplicasAreRefusedEverywhere() {
        assertThatThrownBy(() -> settings(1, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minInsyncReplicas");
    }

    @Test
    void theBrokersMustBeNamed() {
        assertThatThrownBy(() -> KafkaClientSettings.of(" ", Map.of(), KafkaEventSettings.defaults(), false))
            .isInstanceOf(IllegalStateException.class);
    }

    private static Map<String, String> mutualTls() {
        Map<String, String> client = new LinkedHashMap<>();
        client.put("security.protocol", "SSL");
        client.put("ssl.keystore.type", "PKCS12");
        client.put("ssl.keystore.location", "/etc/brix/kafka/client.p12");
        client.put("ssl.keystore.password", "from-the-secret-store");
        client.put("ssl.truststore.type", "PKCS12");
        client.put("ssl.truststore.location", "/etc/brix/kafka/trust.p12");
        client.put("ssl.truststore.password", "from-the-secret-store");
        return client;
    }

    private static KafkaEventSettings settings(int replicas, int inSync) {
        return new KafkaEventSettings(3, (short) replicas, (short) inSync, Duration.ofDays(7), 10,
            Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10));
    }
}
