/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.simple.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.infra.adapter.simple.InMemoryEventBusCapability;
import io.runtime.sdk.capability.EventBusCapability;

/**
 * The in-memory event bus is a development adapter (root BRX-RS-BOOT-003; Kafka design §13 K5).
 */
class SimpleAdapterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SimpleAdapterAutoConfiguration.class))
        .withPropertyValues("brix.infra.simple.enabled=true");

    @Test
    void theInMemoryEventBusStartsOutsideTheProductionProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=embedded").run(context ->
            assertThat(context).getBean(EventBusCapability.class).isInstanceOf(InMemoryEventBusCapability.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void theProductionProfileDoesNotStartWithTheInMemoryEventBus(String profile) {
        contextRunner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("BRX-RS-BOOT-003");
        });
    }
}
