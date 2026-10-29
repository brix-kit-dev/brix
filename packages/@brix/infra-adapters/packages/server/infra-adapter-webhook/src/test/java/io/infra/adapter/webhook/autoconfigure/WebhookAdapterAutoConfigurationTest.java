/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.webhook.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.infra.adapter.webhook.HttpWebhookEventBus;
import io.runtime.sdk.capability.EventBusCapability;

/**
 * The webhook event bus is a development adapter (root BRX-RS-BOOT-003; Kafka design §13 K5).
 */
class WebhookAdapterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebhookAdapterAutoConfiguration.class))
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .withPropertyValues(
            "brix.infra.webhook.enabled=true",
            "brix.infra.webhook.default-endpoint=http://127.0.0.1:9/events",
            "brix.infra.webhook.signature-enabled=false");

    @Test
    void theWebhookEventBusStartsOutsideTheProductionProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=embedded").run(context ->
            assertThat(context).getBean(EventBusCapability.class).isInstanceOf(HttpWebhookEventBus.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void theProductionProfileDoesNotStartWithTheWebhookEventBus(String profile) {
        contextRunner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("BRX-RS-BOOT-003");
        });
    }
}
