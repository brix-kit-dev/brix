/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

/**
 * Kafka design §14 K13: the producer gives up on a record within the publish timeout, so a send the relay
 * records as failed is not still under way.
 */
class KafkaDeliveryTimeoutsTest {

    @Test
    void theProducerGivesUpWithinThePublishTimeout() {
        Map<String, Object> configs = KafkaAutoConfiguration.deliveryTimeouts(30000, 5);

        assertThat(configs).containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000)
            .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 29995)
            .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 30000L);
    }

    @Test
    void aRequestNeverWaitsLongerThanThirtySeconds() {
        assertThat(KafkaAutoConfiguration.deliveryTimeouts(120000, 5))
            .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
    }

    @Test
    void aPublishTimeoutShorterThanTheLingerIsRefused() {
        assertThatThrownBy(() -> KafkaAutoConfiguration.deliveryTimeouts(900, 5))
            .isInstanceOf(IllegalStateException.class);
    }
}
