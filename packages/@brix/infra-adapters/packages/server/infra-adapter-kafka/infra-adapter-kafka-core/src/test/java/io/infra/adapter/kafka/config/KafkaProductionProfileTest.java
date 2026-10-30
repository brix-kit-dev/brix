/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.infra.adapter.kafka.KafkaClientSettings;

/**
 * A host under the production profile does not start with an insecure or unreliable Kafka configuration (root
 * BRX-RS-BOOT-003, SECURITY-006; Kafka design D35, §14.5 item 4).
 */
class KafkaProductionProfileTest {

    private final ApplicationContextRunner host = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
        .withPropertyValues("brix.infra.kafka.bootstrap-servers=localhost:9", "brix.outbox.relay.transport=kafka");

    @Test
    void theProductionProfileRefusesPlaintextAndASingleReplica() {
        for (String profile : new String[] {"prod", "production"}) {
            host.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining("production profile refuses this Kafka configuration")
                        .hasMessageContaining("security.protocol must be SSL")
                        .hasMessageContaining("replication-factor must be at least 3")
                        .hasMessageContaining("min-insync-replicas must be at least 2");
                });
        }
    }

    @Test
    void theProductionProfileRefusesTlsWithoutAClientCertificate() {
        host.withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
            .withPropertyValues(
                "brix.infra.kafka.client.security.protocol=SSL",
                "brix.infra.kafka.client.ssl.truststore.location=/etc/brix/kafka/trust.p12",
                "brix.infra.kafka.events.replication-factor=3",
                "brix.infra.kafka.events.min-insync-replicas=2")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("client certificate is missing");
            });
    }

    @Test
    void aSettingTheAdapterOwnsStopsAnyHost() {
        host.withPropertyValues("brix.infra.kafka.client.enable.auto.commit=true")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("enable.auto.commit");
            });
    }

    @Test
    void outsideProductionTheSharedSingleNodeSetupStarts() {
        host.withInitializer(context -> context.getEnvironment().setActiveProfiles("standalone"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(KafkaClientSettings.class).securityProtocol()).isEqualTo("PLAINTEXT");
            });
    }
}
