/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Contract tests for the Spring Host path that assembles the L2B relay.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
class OutboxRelayAutoConfigurationTest {

    @Test
    void oneRelayRunsForEachOwnerOutbox() {
        OutboxRelayProperties properties = properties();
        OutboxRelayPolicy policy = new OutboxRelayAutoConfiguration().outboxRelayPolicy(properties);
        List<String> metered = new ArrayList<>();
        OutboxRelayGroup group = new OutboxRelayGroup(properties, policy,
            () -> () -> java.util.Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore(),
                "platform_identity_outbox", new EmptyOutboxMessageStore()),
            () -> ignored -> {
            },
            outbox -> {
                metered.add(outbox);
                return OutboxRelayMetrics.NOOP;
            });

        assertEquals(25, policy.batchSize());
        assertDoesNotThrow(group::start);
        assertTrue(group.isRunning());
        assertEquals(Set.of("platform_tenant_outbox", "platform_identity_outbox"), Set.copyOf(metered));
        group.stop();
        assertFalse(group.isRunning());
    }

    @Test
    void stoppingTheGroupTellsRunningDeliveriesToStop() {
        AtomicBoolean stopped = new AtomicBoolean();
        OutboxTransport transport = new OutboxTransport() {
            @Override
            public void publish(CanonicalOutboxMessage message) {
            }

            @Override
            public void stop() {
                stopped.set(true);
            }
        };
        OutboxRelayGroup group = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(),
            () -> () -> java.util.Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore()), () -> transport,
            OutboxRelayMetricsFactory.NOOP);
        group.start();

        group.stop();

        assertTrue(stopped.get());
    }

    @Test
    void aHostWithMicrometerAndActuatorGetsTheRelayMetersAndTheOutboxEndpoint() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("relay", Map.of(
                "brix.outbox.relay.enabled", "true",
                "brix.outbox.relay.owner", "enterprise-shell",
                "brix.outbox.relay.transport", "kafka")));
            context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
            context.registerBean(OutboxStoreRegistry.class,
                () -> () -> Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore()));
            context.registerBean(OutboxTransport.class, () -> message -> {
            });
            context.register(OutboxRelayAutoConfiguration.class);
            context.refresh();

            assertTrue(context.getBean(OutboxRelayGroup.class).isRunning());
            assertNotNull(context.getBean(OutboxEndpoint.class));
            assertNotNull(context.getBean(MeterRegistry.class).find(MicrometerOutboxRelayMetrics.MESSAGES)
                .tags("outbox", "platform_tenant_outbox", "status", "parked").gauge());
        }
    }

    @Test
    void enabledRelayRequiresStableOwnerIdentity() {
        OutboxRelayGroup group = new OutboxRelayGroup(new OutboxRelayProperties(), OutboxRelayPolicy.defaults(),
            () -> java.util.Map::of, () -> ignored -> {
            }, OutboxRelayMetricsFactory.NOOP);

        IllegalStateException failure = assertThrows(IllegalStateException.class, group::start);

        assertEquals("brix.outbox.relay.owner must be configured when the relay is enabled", failure.getMessage());
    }

    @Test
    void anEnabledRelayWithoutOutboxesOrTransportStopsTheHost() {
        OutboxRelayGroup withoutStores = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(), () -> null,
            () -> ignored -> {
            }, OutboxRelayMetricsFactory.NOOP);
        OutboxRelayGroup withoutTransport = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(),
            () -> java.util.Map::of, () -> null, OutboxRelayMetricsFactory.NOOP);

        assertTrue(assertThrows(IllegalStateException.class, withoutStores::start).getMessage()
            .contains("brix.outbox.relay.persistence"));
        assertTrue(assertThrows(IllegalStateException.class, withoutTransport::start).getMessage()
            .contains("in-process"));
    }

    private static OutboxRelayProperties properties() {
        OutboxRelayProperties properties = new OutboxRelayProperties();
        properties.setOwner("enterprise-shell");
        properties.setPollDelay(Duration.ofMillis(100));
        return properties;
    }

    private static final class EmptyOutboxMessageStore implements OutboxMessageStore {

        private final List<CanonicalOutboxMessage> appended = new ArrayList<>();

        @Override
        public void append(CanonicalOutboxMessage message) {
            appended.add(message);
        }

        @Override
        public List<CanonicalOutboxMessage> claimDue(
                String relayOwner,
                OffsetDateTime now,
                Duration leaseDuration,
                int batchSize) {
            return List.of();
        }

        @Override
        public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
        }

        @Override
        public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
        }

        @Override
        public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
        }

        @Override
        public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
            return new OutboxBacklogSnapshot(appended.size(), 0L, 0L, Duration.ZERO);
        }

        @Override
        public List<ParkedOutboxMessage> listParked(int limit) {
            return List.of();
        }

        @Override
        public boolean replayParked(String eventId, OffsetDateTime now) {
            return false;
        }
    }
}
